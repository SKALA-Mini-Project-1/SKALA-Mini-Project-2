package com.example.SKALA_Mini_Project_1.kafka;

import java.util.UUID;

/**
 * ticketing.events.v1 토픽으로 발행하는 티케팅 이벤트 메시지 구조.
 */
public record TicketingOutboxMessage(
        UUID eventId,
        String eventType,
        String eventVersion,
        String producer,
        String aggregateType,
        String aggregateId,
        String orderingKey,
        UUID bookingId,
        UUID paymentId,
        String correlationId,
        String occurredAt,
        String payloadJson
) {
}
