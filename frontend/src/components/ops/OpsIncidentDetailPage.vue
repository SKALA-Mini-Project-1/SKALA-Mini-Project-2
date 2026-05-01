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

function parseCurrentState(json: string | null): Record<string, string> {
  if (!json) return {}
  try { return JSON.parse(json) } catch { return {} }
}

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
            <div v-if="incident.currentState" class="snapshot-grid">
              <div
                v-for="(val, key) in parseCurrentState(incident.currentState)"
                :key="key"
                class="snapshot-item"
              >
                <p class="snapshot-key muted">{{ key }}</p>
                <p class="snapshot-val">{{ val }}</p>
              </div>
            </div>
            <p v-else class="muted" style="font-size:13px;">스냅샷 없음</p>
          </div>

          <!-- 관련 리소스 -->
          <div class="ops-card resource-card">
            <p class="section-title">🔗 관련 리소스</p>
            <div class="resource-list">
              <div class="resource-row">
                <span class="muted">primaryPaymentId</span>
                <code>{{ incident.primaryPaymentId ?? '—' }}</code>
              </div>
              <div class="resource-row">
                <span class="muted">primaryBookingId</span>
                <code>{{ incident.primaryBookingId ?? '—' }}</code>
              </div>
              <div class="resource-row">
                <span class="muted">userId</span>
                <code>{{ incident.userId ?? '—' }}</code>
              </div>
              <div class="resource-row">
                <span class="muted">concertId</span>
                <code>{{ incident.concertId ?? '—' }}</code>
              </div>
              <div class="resource-row">
                <span class="muted">scheduleId</span>
                <code>{{ incident.scheduleId ?? '—' }}</code>
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
.detail-wrap { display: flex; flex-direction: column; gap: 16px; color: #e2eaf4; }

.back-btn {
  background: none; border: none; color: #7a9ab8;
  font-size: 13px; cursor: pointer; padding: 0; width: fit-content;
}
.back-btn:hover { color: #e2eaf4; }

.ops-card {
  background: #0d1e35;
  border: 1px solid #1e3553;
  border-radius: 16px;
  padding: 20px 24px;
}

.center-msg { text-align: center; color: #7a9ab8; padding: 32px; }
.error-banner { color: #fca5a5; background: #450a0a; border-color: #7f1d1d; }
.action-msg { color: #86efac; background: #052e16; border-color: #166534; cursor: pointer; font-size: 13px; }

/* 헤더 */
.detail-header { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: 16px; }
.header-left   { display: flex; flex-direction: column; gap: 8px; }
.header-badges { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.type-title    { font-size: 18px; font-weight: 800; }
.header-meta   { display: flex; gap: 16px; font-size: 12px; flex-wrap: wrap; }
.header-meta code { font-family: monospace; }
.header-actions { display: flex; gap: 8px; flex-wrap: wrap; }

/* 2컬럼 */
.two-col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 960px) { .two-col { grid-template-columns: 1fr; } }

.right-col { display: flex; flex-direction: column; gap: 16px; }

/* 분석 카드 */
.analysis-card { display: flex; flex-direction: column; gap: 16px; }
.section-header { display: flex; align-items: center; gap: 10px; }
.section-title  { font-weight: 700; font-size: 14px; }

.confidence-wrap { display: flex; flex-direction: column; gap: 4px; }
.confidence-label { display: flex; justify-content: space-between; font-size: 12px; }
.confidence-val  { font-weight: 700; color: #ff7a00; }
.confidence-track { background: #1e3553; border-radius: 4px; height: 8px; overflow: hidden; }
.confidence-fill  { height: 100%; background: #ff7a00; border-radius: 4px; transition: width 0.3s; }

.block-box   { background: #112040; border: 1px solid #1e3553; border-radius: 10px; padding: 12px 14px; }
.block-label { font-size: 11px; font-weight: 600; color: #7a9ab8; margin-bottom: 4px; }
.block-text  { font-size: 13px; line-height: 1.6; }
.muted-light { color: #cbd5e1; font-size: 13px; line-height: 1.6; }

.action-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
.action-item { display: flex; gap: 10px; align-items: flex-start; font-size: 13px; }
.action-num  { background: #ff7a00; color: #fff; border-radius: 999px; min-width: 20px; height: 20px; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; flex-shrink: 0; margin-top: 1px; }
.action-title { font-weight: 600; }

.approval-banner { display: flex; align-items: flex-start; gap: 12px; background: #7c2d12; border: 1px solid #f97316; border-radius: 10px; padding: 12px 14px; }
.approval-title  { font-size: 13px; font-weight: 700; color: #fdba74; }
.approval-sub    { font-size: 12px; color: #fcd9af; margin-top: 2px; }

/* 스냅샷 */
.snapshot-card, .resource-card, .analysis-status-card { padding: 18px 20px; }
.snapshot-grid  { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 10px; }
.snapshot-item  { background: #112040; border: 1px solid #1e3553; border-radius: 8px; padding: 8px 10px; }
.snapshot-key   { font-size: 11px; margin-bottom: 2px; }
.snapshot-val   { font-size: 13px; font-weight: 700; font-family: monospace; }

.resource-list { display: flex; flex-direction: column; gap: 6px; margin-top: 10px; font-size: 13px; }
.resource-row  { display: flex; justify-content: space-between; align-items: center; }
.resource-row code { font-family: monospace; font-size: 12px; }

/* 신호 */
.signal-card  { padding: 18px 20px; }
.signal-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.signal-chip  { display: inline-flex; align-items: center; background: #112040; border: 1px solid #1e3553; border-radius: 6px; padding: 4px 10px; font-size: 12px; color: #7a9ab8; }

/* 탭 */
.tab-card    { padding: 0; overflow: hidden; }
.tab-bar     { display: flex; border-bottom: 1px solid #1e3553; }
.tab-btn     { padding: 12px 20px; font-size: 13px; font-weight: 600; background: none; border: none; cursor: pointer; }
.tab-active  { border-bottom: 2px solid #ff7a00; color: #ff7a00; margin-bottom: -1px; }
.tab-inactive { color: #7a9ab8; }
.tab-inactive:hover { color: #e2eaf4; }
.tab-pane    { padding: 20px 24px; display: flex; flex-direction: column; gap: 12px; }

.version-row { background: #112040; border: 1px solid #1e3553; border-radius: 10px; padding: 14px 16px; }
.version-header { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 6px; }
.version-summary { font-size: 13px; line-height: 1.5; }
.version-failure { font-size: 12px; color: #fca5a5; margin-top: 4px; }
.trigger-chip { background: #0d1e35; border: 1px solid #1e3553; border-radius: 4px; padding: 2px 8px; font-size: 11px; color: #7a9ab8; }
.latest-chip  { background: #1e3a5f; color: #93c5fd; border-radius: 999px; padding: 2px 8px; font-size: 11px; font-weight: 600; }
.opacity-60   { opacity: 0.65; }

.history-row   { display: flex; align-items: flex-start; gap: 14px; }
.history-item  { background: #112040; border: 1px solid #1e3553; border-radius: 10px; padding: 10px 14px; flex: 1; display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.history-actor { font-weight: 600; font-size: 13px; }
.arrow         { font-size: 13px; }
.history-note  { width: 100%; font-size: 12px; margin-top: 4px; }
.time-cell     { font-size: 11px; white-space: nowrap; padding-top: 12px; flex-shrink: 0; min-width: 120px; }

/* 배지 공통 */
.badge { display: inline-block; padding: 3px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; white-space: nowrap; }
.badge-critical { background: #7f1d1d; color: #fca5a5; }
.badge-high     { background: #7c2d12; color: #fdba74; }
.badge-medium   { background: #713f12; color: #fde68a; }
.badge-low      { background: #1e3a5f; color: #93c5fd; }

.status-badge { display: inline-block; padding: 3px 8px; border-radius: 999px; font-size: 11px; font-weight: 600; white-space: nowrap; }
.status-open         { background: #1e3a5f; color: #93c5fd; }
.status-analyzing    { background: #312e81; color: #c4b5fd; }
.status-analyzed     { background: #164e63; color: #67e8f9; }
.status-acknowledged { background: #374151; color: #d1d5db; }
.status-resolved     { background: #14532d; color: #86efac; }

/* 버튼 */
.btn-orange {
  background: #ff7a00; color: #fff; border: none;
  border-radius: 10px; padding: 8px 16px; font-size: 13px; font-weight: 700; cursor: pointer;
}
.btn-orange:hover:not(:disabled) { background: #e86f00; }
.btn-orange:disabled { opacity: 0.4; cursor: not-allowed; }

.btn-ghost {
  background: transparent; border: 1px solid #1e3553; color: #e2eaf4;
  border-radius: 10px; padding: 8px 16px; font-size: 13px; font-weight: 600; cursor: pointer;
}
.btn-ghost:hover:not(:disabled) { background: #112040; }
.btn-ghost:disabled { opacity: 0.4; cursor: not-allowed; }

.muted { color: #7a9ab8; }
</style>
