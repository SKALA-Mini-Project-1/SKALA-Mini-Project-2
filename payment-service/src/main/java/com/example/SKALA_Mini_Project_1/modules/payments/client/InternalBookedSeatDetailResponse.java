package com.example.SKALA_Mini_Project_1.modules.payments.client;

import java.math.BigDecimal;

public record InternalBookedSeatDetailResponse(
        Long seatId,
        String section,
        Integer rowNumber,
        Integer seatNumber,
        String grade,
        BigDecimal price
) {
}
