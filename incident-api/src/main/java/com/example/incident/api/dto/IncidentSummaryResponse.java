package com.example.incident.api.dto;

import com.example.incident.api.domain.Incident;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IncidentSummaryResponse(
        UUID incidentId,
        String incidentType,
        String severity,
        String status,
        String latestSummary,
        String latestAnalysisStatus,
        Boolean needsHumanApproval,
        OffsetDateTime firstDetectedAt,
        OffsetDateTime updatedAt
) {
    public static IncidentSummaryResponse from(Incident incident, String latestSummary, String latestAnalysisStatus) {
        return new IncidentSummaryResponse(
                incident.getIncidentId(),
                incident.getIncidentType(),
                incident.getSeverity(),
                incident.getStatus(),
                latestSummary,
                latestAnalysisStatus,
                incident.getNeedsHumanApproval(),
                incident.getFirstDetectedAt(),
                incident.getUpdatedAt()
        );
    }
}
