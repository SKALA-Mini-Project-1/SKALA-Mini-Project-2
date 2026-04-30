package com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InternalBookingCancelRequest(
        UUID paymentId,
        String pgOrderId,
        String reasonCode,
        OffsetDateTime canceledAt,
        UUID bookingId
) {
}
