package com.example.SKALA_Mini_Project_1.integration.userauth;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InternalFanScoreApplyRequest(
        Long userId,
        UUID bookingId,
        Long concertId,
        Long artistId,
        OffsetDateTime occurredAt
) {
}
