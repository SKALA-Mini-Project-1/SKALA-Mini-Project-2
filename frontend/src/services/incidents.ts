export interface IncidentSummary {
  incidentId: string
  incidentType: string
  severity: string
  status: string
  latestSummary: string | null
  latestAnalysisStatus: string | null
  needsHumanApproval: boolean | null
  firstDetectedAt: string
  updatedAt: string
}

export interface AnalysisVersion {
  analysisVersionId: string
  versionNumber: number
  analysisStatus: string
  triggerType: string | null
  requestedBy: string | null
  summaryText: string | null
  outputJson: string | null
  llmModel: string | null
  promptTokens: number | null
  completionTokens: number | null
  latencyMs: number | null
  failureReason: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
}

export interface IncidentDetail {
  incidentId: string
  incidentType: string
  incidentKey: string
  severity: string
  status: string
  primaryPaymentId: string | null
  primaryBookingId: string | null
  userId: number | null
  concertId: number | null
  scheduleId: number | null
  openReasonSignal: string | null
  currentState: string | null
  needsHumanApproval: boolean | null
  firstDetectedAt: string
  lastDetectedAt: string
  lastAnalyzedAt: string | null
  resolvedAt: string | null
  resolvedBy: string | null
  updatedAt: string
  latestAnalysis: AnalysisVersion | null
  analysisVersions: AnalysisVersion[]
}

export interface PagedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

async function req<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    const msg = data?.message ?? `요청 실패 (${res.status})`
    throw new Error(msg)
  }
  return data as T
}

export async function fetchIncidents(params: {
  status?: string
  incidentType?: string
  severity?: string
  page?: number
  size?: number
}): Promise<PagedResponse<IncidentSummary>> {
  const q = new URLSearchParams()
  if (params.status)       q.set('status', params.status)
  if (params.incidentType) q.set('incidentType', params.incidentType)
  if (params.severity)     q.set('severity', params.severity)
  if (params.page !== undefined) q.set('page', String(params.page))
  q.set('size', String(params.size ?? 20))
  q.set('sort', 'updatedAt,desc')
  return req(`/ops/incidents?${q}`)
}

export async function fetchIncidentDetail(incidentId: string): Promise<IncidentDetail> {
  return req(`/ops/incidents/${incidentId}`)
}

export async function fetchAnalysisVersions(incidentId: string): Promise<AnalysisVersion[]> {
  return req(`/ops/incidents/${incidentId}/analyses`)
}

export async function acknowledgeIncident(incidentId: string, operatorId: string): Promise<void> {
  await req(`/ops/incidents/${incidentId}/acknowledge`, {
    method: 'POST',
    body: JSON.stringify({ operatorId }),
  })
}

export async function resolveIncident(incidentId: string, operatorId: string): Promise<void> {
  await req(`/ops/incidents/${incidentId}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ operatorId }),
  })
}

export async function reanalyzeIncident(
  incidentId: string,
  requestedBy = 'operator',
): Promise<{ analysisVersionId: string; status: string }> {
  return req(`/ops/incidents/${incidentId}/reanalyze`, {
    method: 'POST',
    body: JSON.stringify({ requestedBy }),
  })
}
