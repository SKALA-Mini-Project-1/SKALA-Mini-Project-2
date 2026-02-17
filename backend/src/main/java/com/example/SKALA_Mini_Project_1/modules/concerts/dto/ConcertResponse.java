package com.example.SKALA_Mini_Project_1.modules.concerts.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ConcertResponse(
        Long id,
        String title,
        String category,
        String description,
        String location,
        Integer durationMinutes,
        Boolean isVisible,
        OffsetDateTime createdAt,
        Long artistId,
        String artistName,
        Long minPrice,
        List<ConcertScheduleResponse> schedules
) {
}
