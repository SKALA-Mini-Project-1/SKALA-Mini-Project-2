package com.example.incident.detector.kafka;

import java.util.UUID;

public record TicketingEventMessage(
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
) {}
