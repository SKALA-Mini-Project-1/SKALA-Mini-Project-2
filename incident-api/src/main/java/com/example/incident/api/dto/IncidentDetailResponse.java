package com.example.incident.api.dto;

import com.example.incident.api.domain.Incident;
import com.example.incident.api.domain.IncidentAnalysisVersion;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record IncidentDetailResponse(
        UUID incidentId,
        String incidentType,
        String incidentKey,
        String severity,
        String status,
        UUID primaryPaymentId,
        UUID primaryBookingId,
        Long userId,
        Long concertId,
        Long scheduleId,
        String openReasonSignal,
        String currentState,
        Boolean needsHumanApproval,
        OffsetDateTime firstDetectedAt,
        OffsetDateTime lastDetectedAt,
        OffsetDateTime lastAnalyzedAt,
        OffsetDateTime resolvedAt,
        String resolvedBy,
        OffsetDateTime updatedAt,
        AnalysisVersionResponse latestAnalysis,
        List<AnalysisVersionResponse> analysisVersions
) {
    public static IncidentDetailResponse from(
            Incident incident,
            AnalysisVersionResponse latestAnalysis,
            List<AnalysisVersionResponse> analysisVersions
    ) {
        return new IncidentDetailResponse(
                incident.getIncidentId(),
                incident.getIncidentType(),
                incident.getIncidentKey(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getPrimaryPaymentId(),
                incident.getPrimaryBookingId(),
                incident.getUserId(),
                incident.getConcertId(),
                incident.getScheduleId(),
                incident.getOpenReasonSignal(),
                incident.getCurrentStateJsonb(),
                incident.getNeedsHumanApproval(),
                incident.getFirstDetectedAt(),
                incident.getLastDetectedAt(),
                incident.getLastAnalyzedAt(),
                incident.getResolvedAt(),
                incident.getResolvedBy(),
                incident.getUpdatedAt(),
                latestAnalysis,
                analysisVersions
        );
    }
}
