package com.example.SKALA_Mini_Project_1.modules.events.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketingInboxEventItemResponse(
        UUID id,
        String dedupeKey,
        String eventType,
        String producer,
        UUID bookingId,
        UUID paymentId,
        String aggregateId,
        String pgOrderId,
        String pgPaymentKey,
        String status,
        Integer duplicateCount,
        OffsetDateTime receivedAt,
        OffsetDateTime lastSeenAt
) {
}
