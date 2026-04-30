package com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing;

import java.util.List;

public record InternalBookingHistoryDetailsResponse(
        List<InternalBookingHistoryDetailResponse> items
) {
}
