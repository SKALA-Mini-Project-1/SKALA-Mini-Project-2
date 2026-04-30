package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

public record PaymentSchedulerHealthResponse(
        long expireSchedulerDelayMs,
        long pendingCount,
        long payingCount,
        long expiredCount,
        long refundRequiredCount
) {
}
