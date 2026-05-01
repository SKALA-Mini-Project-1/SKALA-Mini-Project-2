package com.example.incident.detector.incident.dto;

import java.util.UUID;

public record IncidentCreateCommand(
        String incidentType,
        String incidentKey,
        String severity,
        String signal,
        String currentStateJson,
        UUID primaryPaymentId,
        UUID primaryBookingId,
        Long userId,
        Long concertId,
        Long scheduleId
) {}
