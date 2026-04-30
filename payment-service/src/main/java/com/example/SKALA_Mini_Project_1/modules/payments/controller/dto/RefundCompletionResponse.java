package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundCompletionResponse(
        UUID paymentId,
        String paymentStatus,
        UUID refundId,
        String refundStatus,
        OffsetDateTime completedAt,
        String pgRefundId
) {
}
