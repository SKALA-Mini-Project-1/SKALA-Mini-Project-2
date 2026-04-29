package com.example.SKALA_Mini_Project_1.modules.payments.client;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InternalBookingFinalizationResponse(
        UUID bookingId,
        UUID paymentId,
        String outcome,
        String bookingStatus,
        boolean holdValid,
        Long userId,
        Long concertId,
        Long scheduleId,
        List<Long> seatIds,
        String reasonCode,
        String pgOrderId,
        String pgPaymentKey,
        Long amount,
        OffsetDateTime processedAt
) {
}
