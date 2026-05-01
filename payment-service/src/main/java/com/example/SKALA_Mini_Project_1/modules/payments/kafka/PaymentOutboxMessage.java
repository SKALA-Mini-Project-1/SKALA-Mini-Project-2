package com.example.SKALA_Mini_Project_1.modules.payments.kafka;

import java.util.UUID;

public record PaymentOutboxMessage(
        UUID eventId,
        String eventType,
        String eventVersion,
        String producer,
        String aggregateType,
        String aggregateId,
        String orderingKey,
        UUID paymentId,
        UUID bookingId,
        String pgOrderId,
        String fromStatus,
        String toStatus,
        String correlationId,
        String causationId,
        String traceId,
        String idempotencyKey,
        String occurredAt,
        String payloadJson
) {
}
