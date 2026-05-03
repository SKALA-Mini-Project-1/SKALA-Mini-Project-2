<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  fetchIncidentDetail,
  acknowledgeIncident,
  resolveIncident,
  reanalyzeIncident,
  type IncidentDetail,
  type AnalysisVersion,
} from '../../services/incidents'

const props = defineProps<{ incidentId: string }>()
const emit  = defineEmits<{ back: [] }>()

const incident  = ref<IncidentDetail | null>(null)
const loading   = ref(false)
const error     = ref<string | null>(null)
const actionMsg = ref<string | null>(null)
const activeTab = ref<'analysis' | 'history'>('analysis')

async function load() {
  loading.value = true
  error.value   = null
  try {
    incident.value = await fetchIncidentDetail(props.incidentId)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '불러오기 실패'
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function doAcknowledge() {
  if (!incident.value) return
  try {
    await acknowledgeIncident(props.incidentId, 'operator')
    await load()
    actionMsg.value = '확인 완료 처리되었습니다.'
  } catch (e) {
    actionMsg.value = e instanceof Error ? e.message : '처리 실패'
  }
}

async function doResolve() {
  if (!incident.value) return
  try {
    await resolveIncident(props.incidentId, 'operator')
    await load()
    actionMsg.value = '해결됨으로 처리되었습니다.'
  } catch (e) {
    actionMsg.value = e instanceof Error ? e.message : '처리 실패'
  }
}

async function doReanalyze() {
  try {
    await reanalyzeIncident(props.incidentId, 'operator')
    actionMsg.value = '재분석 요청이 접수되었습니다.'
    setTimeout(load, 3000)
  } catch (e) {
    actionMsg.value = e instanceof Error ? e.message : '재분석 요청 실패'
  }
}

const canAcknowledge = computed(() =>
  incident.value?.status === 'ANALYZED'
)
const canResolve = computed(() =>
  incident.value?.status === 'ACKNOWLEDGED'
)

const SEVERITY_LABEL: Record<string, string> = {
  critical: '🔴 CRITICAL', high: '🟠 HIGH', medium: '🟡 MEDIUM', low: '🔵 LOW',
}
const SEVERITY_CLASS: Record<string, string> = {
  critical: 'badge-critical', high: 'badge-high', medium: 'badge-medium', low: 'badge-low',
}
const STATUS_CLASS: Record<string, string> = {
  OPEN: 'status-open', ANALYZING: 'status-analyzing', ANALYZED: 'status-analyzed',
  ACKNOWLEDGED: 'status-acknowledged', RESOLVED: 'status-resolved',
}
const TYPE_LABEL: Record<string, string> = {
  DUPLICATE_PAYMENT: '중복 결제', GHOST_ORDER: '유령 주문',
  ZOMBIE_HOLD: '좀비 예약', UNCONFIRMED_PAYMENT: '미확정 결제',
}

function fmt(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

function parseCurrentState(json: string | null): Record<string, unknown> {
  if (!json) return {}
  try { return JSON.parse(json) } catch { return {} }
}

function parseNestedJson(value: string | null | undefined): Record<string, unknown> {
  if (!value) return {}
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed as Record<string, unknown> : {}
  } catch {
    return {}
  }
}

function isIsoDate(str: string): boolean {
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(str)
}

function toDisplayString(val: unknown): string | null {
  if (val === null || val === undefined || val === '') return null
  if (typeof val === 'string') return isIsoDate(val) ? fmt(val) : val
  if (typeof val === 'number') return String(val)
  return null
}

const stateSnapshot = computed(() => parseCurrentState(incident.value?.currentState ?? null))

const nestedExtraJson = computed(() => {
  const extraJson = stateSnapshot.value.extraJson
  return typeof extraJson === 'string' ? parseNestedJson(extraJson) : {}
})

// extraJson을 펼치고, timeline·extraJson 키 자체를 제거한 평탄화 스냅샷
const flatSnapshot = computed((): Array<{ key: string; val: string }> => {
  const result: Array<{ key: string; val: string }> = []
  const skipKeys = new Set(['extraJson', 'timeline'])

  for (const [key, val] of Object.entries(stateSnapshot.value)) {
    if (skipKeys.has(key)) continue
    const display = toDisplayString(val)
    if (display !== null) result.push({ key, val: display })
  }

  // extraJson 내부 필드를 펼쳐서 추가 (paymentId·bookingId는 관련 리소스에 이미 있으므로 중복 제거)
  const resourceKeys = new Set(['paymentId', 'bookingId', 'userId', 'concertId', 'scheduleId'])
  for (const [key, val] of Object.entries(nestedExtraJson.value)) {
    if (resourceKeys.has(key)) continue
    const display = toDisplayString(val)
    if (display !== null) result.push({ key, val: display })
  }

  return result
})

// timeline 배열 따로 추출
const snapshotTimeline = computed((): Array<Record<string, string>> => {
  const timeline = stateSnapshot.value.timeline
  if (!Array.isArray(timeline)) return []
  return timeline as Array<Record<string, string>>
})

function asDisplayValue(value: unknown): string | number | null {
  if (value === null || value === undefined) return null
  if (typeof value === 'string') return value
  if (typeof value === 'number') return value
  return null
}

function resourceValue(
  primary: string | number | null | undefined,
  ...fallbacks: unknown[]
): string | number | null {
  if (primary !== null && primary !== undefined && primary !== '') {
    return primary
  }
  for (const fallback of fallbacks) {
    const resolved = asDisplayValue(fallback)
    if (resolved !== null && resolved !== '') {
      return resolved
    }
  }
  return null
}

const relatedResources = computed(() => {
  const current = incident.value
  if (!current) {
    return {
      primaryPaymentId: null,
      primaryBookingId: null,
      userId: null,
      concertId: null,
      scheduleId: null,
    }
  }

  return {
    primaryPaymentId: resourceValue(
      current.primaryPaymentId,
      stateSnapshot.value.paymentId,
      nestedExtraJson.value.paymentId,
    ),
    primaryBookingId: resourceValue(
      current.primaryBookingId,
      stateSnapshot.value.bookingId,
      nestedExtraJson.value.bookingId,
    ),
    userId: resourceValue(
      current.userId,
      stateSnapshot.value.userId,
      nestedExtraJson.value.userId,
    ),
    concertId: resourceValue(
      current.concertId,
      stateSnapshot.value.concertId,
      nestedExtraJson.value.concertId,
    ),
    scheduleId: resourceValue(
      current.scheduleId,
      stateSnapshot.value.scheduleId,
      nestedExtraJson.value.scheduleId,
    ),
  }
})

function parseOutputJson(json: string | null): { recommendedActions?: Array<{ order: number; action: string; reason: string }>; suspectedRootCause?: string } {
  if (!json) return {}
  try { return JSON.parse(json) } catch { return {} }
}

function confidencePct(v: AnalysisVersion | null): number {
  if (!v?.outputJson) return 0
  try {
    const o = JSON.parse(v.outputJson)
    return Math.round((o.confidence ?? 0) * 100)
  } catch { return 0 }
}
</script>

<template>
  <div class="detail-wrap">

    <!-- 브레드크럼 -->
    <button class="back-btn" @click="emit('back')">← 목록으로</button>

    <!-- 로딩 / 에러 -->
    <div v-if="loading" class="ops-card center-msg">불러오는 중…</div>
    <div v-else-if="error" class="ops-card error-banner">⚠️ {{ error }}</div>

    <template v-else-if="incident">

      <!-- 액션 메시지 -->
      <div v-if="actionMsg" class="ops-card action-msg" @click="actionMsg = null">
        ✅ {{ actionMsg }} (클릭해서 닫기)
      </div>

      <!-- 헤더 -->
      <div class="ops-card detail-header">
        <div class="header-left">
          <div class="header-badges">
            <span :class="['badge', SEVERITY_CLASS[incident.severity] ?? 'badge-low']">
              {{ SEVERITY_LABEL[incident.severity] ?? incident.severity }}
            </span>
            <span class="type-title">
              {{ TYPE_LABEL[incident.incidentType] ?? incident.incidentType }}
            </span>
            <span :class="['status-badge', STATUS_CLASS[incident.status] ?? 'status-open']">
              {{ incident.status }}
            </span>
          </div>
          <div class="header-meta muted">
            <span>ID: <code>{{ incident.incidentId }}</code></span>
            <span>최초 감지: {{ fmt(incident.firstDetectedAt) }}</span>
            <span>최종 갱신: {{ fmt(incident.updatedAt) }}</span>
          </div>
        </div>
        <div class="header-actions">
          <button
            class="btn-orange"
            :disabled="!canAcknowledge"
            @click="doAcknowledge"
          >✅ 확인 완료</button>
          <button
            class="btn-ghost"
            :disabled="!canResolve"
            @click="doResolve"
          >🔧 해결됨</button>
          <button class="btn-ghost" @click="doReanalyze">🔄 재분석 요청</button>
        </div>
      </div>

      <!-- 2컬럼: AI 분석 결과 + 상태 스냅샷/리소스 -->
      <div class="two-col">

        <!-- 좌: AI 분석 결과 -->
        <div class="ops-card analysis-card">
          <div class="section-header">
            <span class="section-title">🤖 AI 분석 결과</span>
            <span v-if="incident.latestAnalysis" :class="['status-badge', STATUS_CLASS[incident.latestAnalysis.analysisStatus] ?? 'status-open']">
              v{{ incident.latestAnalysis.versionNumber }} · {{ incident.latestAnalysis.analysisStatus }}
            </span>
            <span v-else class="muted" style="font-size:12px;">분석 없음</span>
          </div>

          <template v-if="incident.latestAnalysis">
            <!-- 신뢰도 -->
            <div class="confidence-wrap">
              <div class="confidence-label">
                <span class="muted">신뢰도</span>
                <span class="confidence-val">{{ confidencePct(incident.latestAnalysis) }}%</span>
              </div>
              <div class="confidence-track">
                <div class="confidence-fill" :style="{ width: confidencePct(incident.latestAnalysis) + '%' }"></div>
              </div>
            </div>

            <!-- 요약 -->
            <div class="block-box" v-if="incident.latestAnalysis.summaryText">
              <p class="block-label">요약</p>
              <p class="block-text">{{ incident.latestAnalysis.summaryText }}</p>
            </div>

            <!-- 추정 원인 -->
            <div v-if="parseOutputJson(incident.latestAnalysis.outputJson).suspectedRootCause">
              <p class="block-label">추정 원인</p>
              <p class="block-text muted-light">{{ parseOutputJson(incident.latestAnalysis.outputJson).suspectedRootCause }}</p>
            </div>

            <!-- 권장 액션 -->
            <div v-if="parseOutputJson(incident.latestAnalysis.outputJson).recommendedActions?.length">
              <p class="block-label">권장 액션</p>
              <ol class="action-list">
                <li
                  v-for="a in parseOutputJson(incident.latestAnalysis.outputJson).recommendedActions"
                  :key="a.order"
                  class="action-item"
                >
                  <span class="action-num">{{ a.order }}</span>
                  <div>
                    <span class="action-title">{{ a.action }}</span>
                    <span class="muted"> — {{ a.reason }}</span>
                  </div>
                </li>
              </ol>
            </div>
          </template>
          <div v-else class="muted" style="font-size:13px; padding: 8px 0;">AI 분석 결과가 없습니다.</div>

          <!-- 인간 승인 배너 -->
          <div v-if="incident.needsHumanApproval" class="approval-banner">
            <span>⚠️</span>
            <div>
              <p class="approval-title">운영자 승인 필요</p>
              <p class="approval-sub">자동 조치 비권장. 상태 변경은 운영자 검증 후 수행하세요.</p>
            </div>
          </div>
        </div>

        <!-- 우: 상태 스냅샷 + 리소스 + 분석 상태 -->
        <div class="right-col">

          <!-- 상태 스냅샷 -->
          <div class="ops-card snapshot-card">
            <p class="section-title">📋 현재 상태 스냅샷</p>
            <template v-if="incident.currentState">
              <div class="snapshot-grid">
                <div
                  v-for="item in flatSnapshot"
                  :key="item.key"
                  class="snapshot-item"
                >
                  <p class="snapshot-key muted">{{ item.key }}</p>
                  <p class="snapshot-val">{{ item.val }}</p>
                </div>
              </div>
              <!-- 이벤트 타임라인 (있는 경우) -->
              <template v-if="snapshotTimeline.length">
                <p class="block-label" style="margin-top:14px;">이벤트 흐름</p>
                <div class="timeline-list">
                  <div
                    v-for="(ev, i) in snapshotTimeline"
                    :key="i"
                    class="timeline-event"
                  >
                    <span class="timeline-dot" />
                    <div class="timeline-body">
                      <span class="timeline-type">{{ ev.eventType }}</span>
                      <span class="muted timeline-source">{{ ev.source }}</span>
                      <span v-if="ev.note" class="muted timeline-note">— {{ ev.note }}</span>
                      <span class="muted timeline-time">{{ isIsoDate(ev.occurredAt) ? fmt(ev.occurredAt) : ev.occurredAt }}</span>
                    </div>
                  </div>
                </div>
              </template>
            </template>
            <p v-else class="muted" style="font-size:13px;">스냅샷 없음</p>
          </div>

          <!-- 관련 리소스 -->
          <div class="ops-card resource-card">
            <p class="section-title">🔗 관련 리소스</p>
            <div class="resource-list">
              <div class="resource-row">
                <span class="muted">primaryPaymentId</span>
                <code>{{ relatedResources.primaryPaymentId ?? '—' }}</code>
              </div>
              <div class="resource-row">
                <span class="muted">primaryBookingId</span>
                <code>{{ relatedResources.primaryBookingId ?? '—' }}</code>
              </div>
              <div class="resource-row">
                <span class="muted">userId</span>
                <code>{{ relatedResources.userId ?? '—' }}</code>
              </div>
              <div class="resource-row">
                <span class="muted">concertId</span>
                <code>{{ relatedResources.concertId ?? '—' }}</code>
              </div>
              <div class="resource-row">
                <span class="muted">scheduleId</span>
                <code>{{ relatedResources.scheduleId ?? '—' }}</code>
              </div>
              <div class="resource-row">
                <span class="muted">openReasonSignal</span>
                <code class="signal-chip">{{ incident.openReasonSignal ?? '—' }}</code>
              </div>
            </div>
          </div>

          <!-- 최신 분석 상태 -->
          <div class="ops-card analysis-status-card">
            <p class="section-title">🧠 분석 상태</p>
            <div v-if="incident.latestAnalysis">
              <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap;">
                <span :class="['status-badge', STATUS_CLASS[incident.latestAnalysis.analysisStatus] ?? 'status-open']">
                  v{{ incident.latestAnalysis.versionNumber }} · {{ incident.latestAnalysis.analysisStatus }}
                </span>
                <span class="muted" style="font-size:12px;">{{ incident.latestAnalysis.llmModel }}</span>
              </div>
              <p class="muted" style="font-size:12px; margin-top:6px;">
                trigger: {{ incident.latestAnalysis.triggerType ?? '—' }}
                <span v-if="incident.latestAnalysis.latencyMs"> · {{ incident.latestAnalysis.latencyMs }}ms</span>
              </p>
              <p v-if="incident.latestAnalysis.failureReason" class="muted" style="font-size:12px; color:#fca5a5;">
                실패 사유: {{ incident.latestAnalysis.failureReason }}
              </p>
            </div>
            <p v-else class="muted" style="font-size:13px;">분석 없음</p>
          </div>

        </div>
      </div>

      <!-- Detector 신호 -->
      <div class="ops-card signal-card" v-if="incident.openReasonSignal">
        <p class="section-title">📡 Detector 신호</p>
        <div class="signal-chips">
          <span class="signal-chip">⚡ {{ incident.openReasonSignal }}</span>
        </div>
      </div>

      <!-- 하단 탭: 분석 이력 / 운영자 처리 이력 -->
      <div class="ops-card tab-card">
        <div class="tab-bar">
          <button
            :class="['tab-btn', activeTab === 'analysis' ? 'tab-active' : 'tab-inactive']"
            @click="activeTab = 'analysis'"
          >📊 분석 이력</button>
          <button
            :class="['tab-btn', activeTab === 'history' ? 'tab-active' : 'tab-inactive']"
            @click="activeTab = 'history'"
          >🧑‍💼 운영자 처리 이력</button>
        </div>

        <!-- 분석 이력 -->
        <div v-if="activeTab === 'analysis'" class="tab-pane">
          <div v-if="incident.analysisVersions.length === 0" class="muted center-msg">분석 이력 없음</div>
          <div
            v-for="(v, idx) in incident.analysisVersions"
            :key="v.analysisVersionId"
            class="version-row"
            :class="{ 'opacity-60': idx > 0 }"
          >
            <div class="version-header">
              <span :class="['status-badge', STATUS_CLASS[v.analysisStatus] ?? 'status-open']">
                v{{ v.versionNumber }} · {{ v.analysisStatus }}
              </span>
              <span class="muted" style="font-size:12px;">{{ fmt(v.createdAt) }}</span>
              <span v-if="v.triggerType" class="trigger-chip">{{ v.triggerType }}</span>
              <span v-if="idx === 0" class="latest-chip">최신</span>
            </div>
            <p v-if="v.summaryText" class="version-summary">{{ v.summaryText }}</p>
            <p v-if="v.failureReason" class="version-failure">실패: {{ v.failureReason }}</p>
            <p class="muted" style="font-size:12px; margin-top:4px;">
              {{ v.llmModel ?? '—' }}
              <span v-if="v.latencyMs"> · {{ v.latencyMs }}ms</span>
              <span v-if="v.promptTokens"> · {{ v.promptTokens + (v.completionTokens ?? 0) }}토큰</span>
            </p>
          </div>
        </div>

        <!-- 운영자 처리 이력 (상태 이력은 별도 API 없으므로 incident 필드로 표시) -->
        <div v-if="activeTab === 'history'" class="tab-pane">
          <div class="history-row">
            <span class="muted time-cell">{{ fmt(incident.firstDetectedAt) }}</span>
            <div class="history-item">
              <span class="history-actor">시스템 (incident-detector)</span>
              <span class="arrow muted">→</span>
              <span class="status-badge status-open">OPEN</span>
              <p class="muted history-note">신규 incident 생성. 신호: {{ incident.openReasonSignal ?? '—' }}</p>
            </div>
          </div>
          <div v-if="incident.lastAnalyzedAt" class="history-row">
            <span class="muted time-cell">{{ fmt(incident.lastAnalyzedAt) }}</span>
            <div class="history-item">
              <span class="history-actor">시스템 (incident-agent)</span>
              <span class="arrow muted">→</span>
              <span class="status-badge status-analyzed">ANALYZED</span>
              <p class="muted history-note">최신 분석 완료 (v{{ incident.latestAnalysisVersion ?? '?' }})</p>
            </div>
          </div>
          <div v-if="incident.resolvedAt" class="history-row">
            <span class="muted time-cell">{{ fmt(incident.resolvedAt) }}</span>
            <div class="history-item">
              <span class="history-actor">{{ incident.resolvedBy ?? 'operator' }}</span>
              <span class="arrow muted">→</span>
              <span class="status-badge status-resolved">RESOLVED</span>
            </div>
          </div>
          <div v-if="incident.status === 'OPEN' && !incident.lastAnalyzedAt && !incident.resolvedAt" class="muted center-msg">
            처리 이력 없음
          </div>
        </div>
      </div>

    </template>
  </div>
</template>

<style scoped>
.detail-wrap { display: flex; flex-direction: column; gap: 20px; color: #173451; }

.back-btn {
  background: none; border: none; color: #4f6480;
  font-size: 13px; font-weight: 600; cursor: pointer; padding: 0; width: fit-content; font-family: inherit;
}
.back-btn:hover { color: #173451; }

.ops-card {
  background: #ffffff;
  border: 1px solid #d8e2ef;
  border-radius: 16px;
  padding: 24px;
}

.center-msg { text-align: center; color: #6a819a; padding: 32px; }
.error-banner { color: #b91c1c; background: #fef2f2; border-color: #fca5a5; }
.action-msg { color: #15803d; background: #f0fdf4; border-color: #bbf7d0; cursor: pointer; font-size: 13px; }

/* 헤더 */
.detail-header { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: 16px; }
.header-left   { display: flex; flex-direction: column; gap: 10px; }
.header-badges { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.type-title    { font-size: 20px; font-weight: 800; color: #173451; }
.header-meta   { display: flex; gap: 20px; font-size: 12px; flex-wrap: wrap; color: #6a819a; }
.header-meta code { font-family: monospace; color: #173451; font-weight: 600; }
.header-actions { display: flex; gap: 8px; flex-wrap: wrap; }

/* 2컬럼 */
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
@media (max-width: 960px) { .two-col { grid-template-columns: 1fr; } }

.right-col { display: flex; flex-direction: column; gap: 16px; }

/* 분석 카드 */
.analysis-card { display: flex; flex-direction: column; gap: 18px; }
.section-header { display: flex; align-items: center; gap: 10px; }
.section-title  { font-weight: 800; font-size: 15px; color: #173451; }

.confidence-wrap { display: flex; flex-direction: column; gap: 6px; }
.confidence-label { display: flex; justify-content: space-between; font-size: 12px; color: #6a819a; }
.confidence-val  { font-weight: 700; color: #ff7a00; }
.confidence-track { background: #d6e3f2; border-radius: 4px; height: 7px; overflow: hidden; }
.confidence-fill  { height: 100%; background: #ff7a00; border-radius: 4px; transition: width 0.3s; }

.block-box   { background: #f7fafd; border: 1px solid #d6e3f2; border-radius: 12px; padding: 14px 16px; }
.block-label { font-size: 11px; font-weight: 700; color: #6a819a; margin-bottom: 4px; }
.block-text  { font-size: 13px; line-height: 1.6; color: #173451; }
.muted-light { color: #4f6480; font-size: 13px; line-height: 1.6; }

.action-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
.action-item { display: flex; gap: 10px; align-items: flex-start; font-size: 13px; color: #173451; }
.action-num  { background: #ff7a00; color: #fff; border-radius: 999px; min-width: 22px; height: 22px; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; flex-shrink: 0; margin-top: 1px; }
.action-title { font-weight: 600; }

.approval-banner { display: flex; align-items: flex-start; gap: 12px; background: #fff7ed; border: 1px solid #fdba74; border-radius: 12px; padding: 14px 16px; }
.approval-title  { font-size: 13px; font-weight: 700; color: #c2410c; }
.approval-sub    { font-size: 12px; color: #9a3412; margin-top: 2px; }

/* 스냅샷 */
.snapshot-card, .resource-card, .analysis-status-card { padding: 20px 22px; }
.snapshot-grid  { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 12px; }
.snapshot-item  { background: #f7fafd; border: 1px solid #d6e3f2; border-radius: 10px; padding: 10px 12px; }
.snapshot-key   { font-size: 11px; font-weight: 600; color: #6a819a; margin-bottom: 3px; }
.snapshot-val   { font-size: 13px; font-weight: 700; color: #173451; word-break: break-all; }

.resource-list { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; font-size: 13px; }
.resource-row  { display: flex; justify-content: space-between; align-items: center; }
.resource-row code { font-family: monospace; font-size: 12px; font-weight: 600; color: #173451; }

/* 스냅샷 타임라인 */
.timeline-list  { display: flex; flex-direction: column; gap: 6px; }
.timeline-event { display: flex; align-items: flex-start; gap: 10px; }
.timeline-dot   { width: 8px; height: 8px; border-radius: 50%; background: #ff7a00; flex-shrink: 0; margin-top: 5px; }
.timeline-body  { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; font-size: 12px; }
.timeline-type  { font-weight: 700; color: #173451; }
.timeline-source { background: #f7fafd; border: 1px solid #d6e3f2; border-radius: 6px; padding: 1px 7px; }
.timeline-note  { font-style: italic; }
.timeline-time  { margin-left: auto; white-space: nowrap; }

/* 신호 */
.signal-card  { padding: 20px 22px; }
.signal-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.signal-chip  { display: inline-flex; align-items: center; background: #f7fafd; border: 1px solid #d6e3f2; border-radius: 8px; padding: 4px 12px; font-size: 12px; font-weight: 600; color: #4f6480; }

/* 탭 */
.tab-card    { padding: 0; overflow: hidden; }
.tab-bar     { display: flex; border-bottom: 1px solid #d6e3f2; }
.tab-btn     { padding: 14px 24px; font-size: 13px; font-weight: 600; background: none; border: none; cursor: pointer; font-family: inherit; }
.tab-active  { border-bottom: 2px solid #ff7a00; color: #ff7a00; margin-bottom: -1px; }
.tab-inactive { color: #6a819a; }
.tab-inactive:hover { color: #173451; }
.tab-pane    { padding: 20px 24px; display: flex; flex-direction: column; gap: 12px; }

.version-row { background: #f7fafd; border: 1px solid #d6e3f2; border-radius: 12px; padding: 14px 16px; }
.version-header { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 6px; }
.version-summary { font-size: 13px; line-height: 1.5; color: #173451; }
.version-failure { font-size: 12px; color: #b91c1c; margin-top: 4px; }
.trigger-chip { background: #ffffff; border: 1px solid #d6e3f2; border-radius: 6px; padding: 2px 8px; font-size: 11px; color: #6a819a; }
.latest-chip  { background: #dbeafe; color: #1e40af; border: 1px solid #bfdbfe; border-radius: 999px; padding: 2px 8px; font-size: 11px; font-weight: 600; }
.opacity-60   { opacity: 0.65; }

.history-row   { display: flex; align-items: flex-start; gap: 14px; }
.history-item  { background: #f7fafd; border: 1px solid #d6e3f2; border-radius: 10px; padding: 12px 16px; flex: 1; display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.history-actor { font-weight: 700; font-size: 13px; color: #173451; }
.arrow         { font-size: 13px; color: #6a819a; }
.history-note  { width: 100%; font-size: 12px; margin-top: 4px; color: #6a819a; }
.time-cell     { font-size: 11px; white-space: nowrap; padding-top: 14px; flex-shrink: 0; min-width: 120px; color: #6a819a; font-family: monospace; }

/* 배지 공통 */
.badge { display: inline-block; padding: 3px 10px; border-radius: 999px; font-size: 11px; font-weight: 700; white-space: nowrap; }
.badge-critical { background: #fee2e2; color: #b91c1c; border: 1px solid #fca5a5; }
.badge-high     { background: #ffedd5; color: #c2410c; border: 1px solid #fdba74; }
.badge-medium   { background: #fef9c3; color: #92400e; border: 1px solid #fde68a; }
.badge-low      { background: #dbeafe; color: #1d4ed8; border: 1px solid #93c5fd; }

.status-badge { display: inline-block; padding: 3px 10px; border-radius: 999px; font-size: 11px; font-weight: 600; white-space: nowrap; }
.status-open         { background: #dbeafe; color: #1e40af; border: 1px solid #bfdbfe; }
.status-analyzing    { background: #ede9fe; color: #6d28d9; border: 1px solid #ddd6fe; }
.status-analyzed     { background: #cffafe; color: #0e7490; border: 1px solid #a5f3fc; }
.status-acknowledged { background: #f1f5f9; color: #475569; border: 1px solid #cbd5e1; }
.status-resolved     { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }

/* 버튼 */
.btn-orange {
  background: #ff7a00; color: #fff; border: none;
  border-radius: 10px; padding: 10px 18px; font-size: 13px; font-weight: 700; cursor: pointer; font-family: inherit;
}
.btn-orange:hover:not(:disabled) { background: #e86f00; }
.btn-orange:disabled { opacity: 0.4; cursor: not-allowed; }

.btn-ghost {
  background: transparent; border: 1px solid #d8e2ef; color: #173451;
  border-radius: 10px; padding: 10px 18px; font-size: 13px; font-weight: 600; cursor: pointer; font-family: inherit;
}
.btn-ghost:hover:not(:disabled) { background: #f7fafd; }
.btn-ghost:disabled { opacity: 0.4; cursor: not-allowed; }

.muted { color: #6a819a; }
</style>
