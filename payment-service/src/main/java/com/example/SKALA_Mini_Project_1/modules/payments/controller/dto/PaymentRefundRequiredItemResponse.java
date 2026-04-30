package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentRefundRequiredItemResponse(
        UUID paymentId,
        UUID bookingId,
        Long amount,
        String status,
        String pgOrderId,
        OffsetDateTime updatedAt
) {
}
