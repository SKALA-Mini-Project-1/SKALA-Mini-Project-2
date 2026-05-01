package com.example.SKALA_Mini_Project_1.kafka;

import java.util.UUID;

/**
 * payment.events.v1 토픽에서 소비하는 결제 이벤트 메시지 구조.
 * payment-service의 PaymentOutboxMessage와 동일한 필드 구조를 가진다.
 */
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
