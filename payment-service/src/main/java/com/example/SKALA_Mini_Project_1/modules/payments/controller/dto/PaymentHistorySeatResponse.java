package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import java.math.BigDecimal;

public record PaymentHistorySeatResponse(
        Long seatId,
        String section,
        Integer rowNumber,
        Integer seatNumber,
        String grade,
        BigDecimal price
) {
}
