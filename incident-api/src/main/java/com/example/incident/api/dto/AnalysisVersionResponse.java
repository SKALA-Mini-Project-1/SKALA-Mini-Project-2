package com.example.incident.api.dto;

import com.example.incident.api.domain.IncidentAnalysisVersion;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AnalysisVersionResponse(
        UUID analysisVersionId,
        int versionNumber,
        String analysisStatus,
        String triggerType,
        String requestedBy,
        String summaryText,
        String outputJson,
        String llmModel,
        Integer promptTokens,
        Integer completionTokens,
        Long latencyMs,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
    public static AnalysisVersionResponse from(IncidentAnalysisVersion v) {
        return new AnalysisVersionResponse(
                v.getAnalysisVersionId(),
                v.getVersionNumber(),
                v.getAnalysisStatus(),
                v.getTriggerType(),
                v.getRequestedBy(),
                v.getSummaryText(),
                v.getOutputJsonb(),
                v.getLlmModel(),
                v.getPromptTokens(),
                v.getCompletionTokens(),
                v.getLatencyMs(),
                v.getFailureReason(),
                v.getCreatedAt(),
                v.getStartedAt(),
                v.getCompletedAt()
        );
    }
}
