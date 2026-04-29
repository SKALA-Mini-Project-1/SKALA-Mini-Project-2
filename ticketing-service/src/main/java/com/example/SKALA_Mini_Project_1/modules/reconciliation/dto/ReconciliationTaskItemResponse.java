package com.example.SKALA_Mini_Project_1.modules.reconciliation.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReconciliationTaskItemResponse(
        UUID taskId,
        UUID bookingId,
        UUID paymentId,
        String mismatchType,
        String status,
        String bookingStatus,
        String reasonCode,
        Long userId,
        Long concertId,
        Long scheduleId,
        String seatIdsCsv,
        String pgOrderId,
        String pgPaymentKey,
        Long amount,
        OffsetDateTime requestedAt,
        Integer retryCount,
        String lastError
) {
}
