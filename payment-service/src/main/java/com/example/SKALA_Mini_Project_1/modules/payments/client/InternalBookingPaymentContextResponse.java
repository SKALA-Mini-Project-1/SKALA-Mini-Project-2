package com.example.SKALA_Mini_Project_1.modules.payments.client;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InternalBookingPaymentContextResponse(
        UUID bookingId,
        Long userId,
        Long scheduleId,
        BigDecimal totalPrice,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime canceledAt
) {
}
