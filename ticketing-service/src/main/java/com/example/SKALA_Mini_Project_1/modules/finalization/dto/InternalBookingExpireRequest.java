package com.example.SKALA_Mini_Project_1.modules.finalization.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InternalBookingExpireRequest(
        UUID paymentId,
        String pgOrderId,
        String reasonCode,
        OffsetDateTime expiredAt,
        @NotNull UUID bookingId
) {
}
