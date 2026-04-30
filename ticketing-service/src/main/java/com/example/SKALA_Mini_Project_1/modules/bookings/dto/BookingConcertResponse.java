package com.example.SKALA_Mini_Project_1.modules.bookings.dto;

import java.time.OffsetDateTime;

public record BookingConcertResponse(
        String concertName,
        String venue,
        OffsetDateTime showDateTime,
        Long scheduleId
) {
}
