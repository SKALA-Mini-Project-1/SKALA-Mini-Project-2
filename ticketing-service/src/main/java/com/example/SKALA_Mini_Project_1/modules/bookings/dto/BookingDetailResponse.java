package com.example.SKALA_Mini_Project_1.modules.bookings.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BookingDetailResponse(
        UUID bookingId,
        String status,
        BigDecimal totalPrice,
        OffsetDateTime expiresAt,
        long remainingSeconds,
        boolean payable,
        List<Long> seatIds,
        List<BookedSeatResponse> seats,
        BookingBookerResponse booker,
        BookingConcertResponse concert
) {
}
