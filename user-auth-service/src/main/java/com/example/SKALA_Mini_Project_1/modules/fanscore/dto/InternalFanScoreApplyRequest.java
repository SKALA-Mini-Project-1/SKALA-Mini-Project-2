package com.example.SKALA_Mini_Project_1.modules.fanscore.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record InternalFanScoreApplyRequest(
        @NotNull Long userId,
        @NotNull UUID bookingId,
        @NotNull Long concertId,
        @NotNull Long artistId,
        @NotNull OffsetDateTime occurredAt
) {
}
