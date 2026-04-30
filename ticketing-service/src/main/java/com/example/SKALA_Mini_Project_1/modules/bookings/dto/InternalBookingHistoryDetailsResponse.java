package com.example.SKALA_Mini_Project_1.modules.bookings.dto;

import java.util.List;

public record InternalBookingHistoryDetailsResponse(
        List<InternalBookingHistoryDetailResponse> items
) {
}
