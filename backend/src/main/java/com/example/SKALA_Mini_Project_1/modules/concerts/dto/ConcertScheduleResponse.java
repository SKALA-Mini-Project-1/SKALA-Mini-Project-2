package com.example.SKALA_Mini_Project_1.modules.concerts.dto;

import java.time.OffsetDateTime;

public record ConcertScheduleResponse(
        Long id,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        Integer totalSeats
) {
}
