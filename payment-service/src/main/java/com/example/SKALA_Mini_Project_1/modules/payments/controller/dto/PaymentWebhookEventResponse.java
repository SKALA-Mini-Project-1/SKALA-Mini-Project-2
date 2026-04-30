package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentWebhookEventResponse(
        UUID eventId,
        UUID paymentId,
        String eventType,
        String pgEventId,
        String fromStatus,
        String toStatus,
        OffsetDateTime createdAt,
        OffsetDateTime occurredAt
) {
}
