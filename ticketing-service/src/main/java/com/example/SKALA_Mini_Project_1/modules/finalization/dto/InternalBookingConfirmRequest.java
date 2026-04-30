package com.example.SKALA_Mini_Project_1.modules.finalization.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InternalBookingConfirmRequest(
        UUID paymentId,
        String pgOrderId,
        String pgPaymentKey,
        Long amount,
        OffsetDateTime confirmedAt,
        @NotNull UUID bookingId
) {
}
