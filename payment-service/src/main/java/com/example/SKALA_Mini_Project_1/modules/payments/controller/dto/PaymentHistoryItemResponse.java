package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentHistoryItemResponse(
        UUID paymentId,
        UUID bookingId,
        String paymentStatus,
        Long amount,
        String orderName,
        String pgOrderId,
        OffsetDateTime createdAt,
        OffsetDateTime submittedAt,
        OffsetDateTime paidAt,
        OffsetDateTime updatedAt,
        boolean canRefund,
        String bookingStatus,
        OffsetDateTime bookingConfirmedAt,
        OffsetDateTime bookingCanceledAt,
        String concertName,
        String concertVenue,
        OffsetDateTime showDateTime,
        Integer seatCount,
        List<String> seatLabels,
        List<PaymentHistorySeatResponse> seats
) {
}
