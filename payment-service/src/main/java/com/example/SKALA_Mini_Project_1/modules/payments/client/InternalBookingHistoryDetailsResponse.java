package com.example.SKALA_Mini_Project_1.modules.payments.client;

import java.util.List;

public record InternalBookingHistoryDetailsResponse(
        List<InternalBookingHistoryDetailResponse> items
) {
}
