package com.example.SKALA_Mini_Project_1.modules.payments.client;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InternalBookingHistoryDetailResponse(
        UUID bookingId,
        String bookingStatus,
        OffsetDateTime bookingConfirmedAt,
        OffsetDateTime bookingCanceledAt,
        String concertName,
        String concertVenue,
        OffsetDateTime showDateTime,
        Integer seatCount,
        List<String> seatLabels,
        List<InternalBookedSeatDetailResponse> seats
) {
}
