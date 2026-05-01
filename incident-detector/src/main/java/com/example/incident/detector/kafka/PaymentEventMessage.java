package com.example.incident.detector.kafka;

import java.util.UUID;

public record PaymentEventMessage(
        UUID eventId,
        String eventType,
        String eventVersion,
        String producer,
        String aggregateType,
        String aggregateId,
        String orderingKey,
        UUID paymentId,
        UUID bookingId,
        String pgEventId,
        String fromStatus,
        String toStatus,
        String correlationId,
        String causationId,
        String traceId,
        String idempotencyKey,
        String occurredAt,
        String payloadJson
) {}
