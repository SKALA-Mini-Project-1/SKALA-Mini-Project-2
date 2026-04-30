package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundStatusResponse(
        UUID refundId,
        UUID paymentId,
        String status,
        String reasonCode,
        BigDecimal amount,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt,
        String pgRefundId
) {
}
