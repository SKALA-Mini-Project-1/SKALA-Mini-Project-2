<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { fetchIncidents, type IncidentSummary } from '../../services/incidents'

const emit = defineEmits<{
  openDetail: [incidentId: string]
}>()

const incidents = ref<IncidentSummary[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const filterStatus = ref('')
const filterSeverity = ref('')
const filterType = ref('')
const page = ref(0)
const totalElements = ref(0)
const pageSize = 20

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await fetchIncidents({
      status:       filterStatus.value   || undefined,
      severity:     filterSeverity.value || undefined,
      incidentType: filterType.value     || undefined,
      page:         page.value,
      size:         pageSize,
    })
    incidents.value   = res.content
    totalElements.value = res.totalElements
  } catch (e) {
    error.value = e instanceof Error ? e.message : '불러오기 실패'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch([filterStatus, filterSeverity, filterType], () => {
  page.value = 0
  load()
})

const totalPages = computed(() => Math.ceil(totalElements.value / pageSize))

function applyFilters() {
  page.value = 0
  load()
}

const SEVERITY_LABEL: Record<string, string> = {
  critical: '🔴 CRITICAL',
  high:     '🟠 HIGH',
  medium:   '🟡 MEDIUM',
  low:      '🔵 LOW',
}

const SEVERITY_CLASS: Record<string, string> = {
  critical: 'badge-critical',
  high:     'badge-high',
  medium:   'badge-medium',
  low:      'badge-low',
}

const STATUS_CLASS: Record<string, string> = {
  OPEN:         'status-open',
  ANALYZING:    'status-analyzing',
  ANALYZED:     'status-analyzed',
  ACKNOWLEDGED: 'status-acknowledged',
  RESOLVED:     'status-resolved',
}

const TYPE_LABEL: Record<string, string> = {
  DUPLICATE_PAYMENT:  '중복 결제',
  GHOST_ORDER:        '유령 주문',
  ZOMBIE_HOLD:        '좀비 예약',
  UNCONFIRMED_PAYMENT:'미확정 결제',
}

function formatRelative(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1)   return '방금 전'
  if (mins < 60)  return `${mins}분 전`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24)   return `${hrs}시간 전`
  return `${Math.floor(hrs / 24)}일 전`
}
</script>

<template>
  <div class="ops-wrap">

    <!-- 필터 바 -->
    <div class="ops-card filter-bar">
      <select v-model="filterSeverity" @change="applyFilters">
        <option value="">심각도 전체</option>
        <option value="critical">CRITICAL</option>
        <option value="high">HIGH</option>
        <option value="medium">MEDIUM</option>
        <option value="low">LOW</option>
      </select>
      <select v-model="filterStatus" @change="applyFilters">
        <option value="">상태 전체</option>
        <option value="OPEN">OPEN</option>
        <option value="ANALYZING">ANALYZING</option>
        <option value="ANALYZED">ANALYZED</option>
        <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
        <option value="RESOLVED">RESOLVED</option>
      </select>
      <select v-model="filterType" @change="applyFilters">
        <option value="">유형 전체</option>
        <option value="DUPLICATE_PAYMENT">중복 결제</option>
        <option value="GHOST_ORDER">유령 주문</option>
        <option value="ZOMBIE_HOLD">좀비 예약</option>
        <option value="UNCONFIRMED_PAYMENT">미확정 결제</option>
      </select>
      <button class="btn-ghost refresh-btn" :disabled="loading" @click="load">
        {{ loading ? '로딩 중…' : '🔄 새로고침' }}
      </button>
    </div>

    <!-- 에러 -->
    <div v-if="error" class="ops-card error-banner">
      ⚠️ {{ error }}
    </div>

    <!-- 테이블 -->
    <div class="ops-card table-wrap">
      <table class="incident-table">
        <thead>
          <tr>
            <th>심각도</th>
            <th>유형</th>
            <th>상태</th>
            <th>AI 요약</th>
            <th>최초 감지</th>
            <th>갱신</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6" class="center-cell">불러오는 중…</td>
          </tr>
          <tr v-else-if="incidents.length === 0">
            <td colspan="6" class="center-cell">incident가 없습니다.</td>
          </tr>
          <tr
            v-for="inc in incidents"
            :key="inc.incidentId"
            class="incident-row"
            :class="{ resolved: inc.status === 'RESOLVED' }"
            @click="emit('openDetail', inc.incidentId)"
          >
            <td>
              <span :class="['badge', SEVERITY_CLASS[inc.severity] ?? 'badge-low']">
                {{ SEVERITY_LABEL[inc.severity] ?? inc.severity }}
              </span>
            </td>
            <td class="type-cell">{{ TYPE_LABEL[inc.incidentType] ?? inc.incidentType }}</td>
            <td>
              <span :class="['status-badge', STATUS_CLASS[inc.status] ?? 'status-open']">
                {{ inc.status }}
              </span>
            </td>
            <td class="summary-cell">
              <span v-if="inc.latestAnalysisStatus === 'ANALYZING'" class="muted italic">AI 분석 진행 중…</span>
              <span v-else-if="!inc.latestSummary" class="muted italic">AI 분석 대기 중</span>
              <span v-else>{{ inc.latestSummary }}</span>
            </td>
            <td class="time-cell muted">{{ formatRelative(inc.firstDetectedAt) }}</td>
            <td class="time-cell muted">{{ formatRelative(inc.updatedAt) }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 페이지네이션 -->
      <div v-if="totalPages > 1" class="pagination">
        <span class="muted">총 {{ totalElements }}건</span>
        <div class="page-btns">
          <button
            class="btn-ghost"
            :disabled="page === 0"
            @click="page--; load()"
          >← 이전</button>
          <span class="page-num">{{ page + 1 }} / {{ totalPages }}</span>
          <button
            class="btn-ghost"
            :disabled="page >= totalPages - 1"
            @click="page++; load()"
          >다음 →</button>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.ops-wrap { display: flex; flex-direction: column; gap: 16px; }

.ops-card {
  background: #0d1e35;
  border: 1px solid #1e3553;
  border-radius: 16px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
}

.filter-bar select,
.filter-bar input {
  background: #112040;
  border: 1px solid #1e3553;
  color: #e2eaf4;
  border-radius: 8px;
  padding: 7px 12px;
  font-size: 13px;
}

.refresh-btn {
  margin-left: auto;
  padding: 7px 16px;
  font-size: 13px;
}

.error-banner {
  padding: 14px 20px;
  color: #fca5a5;
  background: #450a0a;
  border-color: #7f1d1d;
}

.table-wrap { overflow: hidden; }

.incident-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.incident-table thead tr {
  background: #112040;
  color: #7a9ab8;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.incident-table th,
.incident-table td {
  padding: 12px 18px;
  text-align: left;
  border-bottom: 1px solid #1e3553;
}

.incident-table tbody tr:last-child td { border-bottom: none; }

.incident-row { cursor: pointer; transition: background 0.12s; }
.incident-row:hover { background: #112040; }
.incident-row.resolved { opacity: 0.55; }

.center-cell { text-align: center; color: #7a9ab8; padding: 32px; }

.badge {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}
.badge-critical { background: #7f1d1d; color: #fca5a5; }
.badge-high     { background: #7c2d12; color: #fdba74; }
.badge-medium   { background: #713f12; color: #fde68a; }
.badge-low      { background: #1e3a5f; color: #93c5fd; }

.status-badge {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.status-open         { background: #1e3a5f; color: #93c5fd; }
.status-analyzing    { background: #312e81; color: #c4b5fd; }
.status-analyzed     { background: #164e63; color: #67e8f9; }
.status-acknowledged { background: #374151; color: #d1d5db; }
.status-resolved     { background: #14532d; color: #86efac; }

.type-cell   { font-weight: 600; }
.summary-cell { color: #7a9ab8; max-width: 360px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.time-cell   { font-size: 12px; white-space: nowrap; }
.muted       { color: #7a9ab8; }
.italic      { font-style: italic; }

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-top: 1px solid #1e3553;
  font-size: 12px;
}
.page-btns { display: flex; align-items: center; gap: 8px; }
.page-num  { color: #e2eaf4; font-weight: 600; }

.btn-ghost {
  background: transparent;
  border: 1px solid #1e3553;
  color: #e2eaf4;
  border-radius: 8px;
  padding: 6px 14px;
  font-size: 12px;
  cursor: pointer;
}
.btn-ghost:hover:not(:disabled) { background: #112040; }
.btn-ghost:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
