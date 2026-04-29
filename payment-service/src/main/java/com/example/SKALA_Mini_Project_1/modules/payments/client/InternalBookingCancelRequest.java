package com.example.SKALA_Mini_Project_1.modules.payments.client;

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
