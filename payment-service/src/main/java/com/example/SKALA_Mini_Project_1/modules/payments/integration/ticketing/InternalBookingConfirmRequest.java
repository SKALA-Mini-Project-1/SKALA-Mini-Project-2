package com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InternalBookingConfirmRequest(
        UUID paymentId,
        String pgOrderId,
        String pgPaymentKey,
        Long amount,
        OffsetDateTime confirmedAt,
        UUID bookingId
) {
}
