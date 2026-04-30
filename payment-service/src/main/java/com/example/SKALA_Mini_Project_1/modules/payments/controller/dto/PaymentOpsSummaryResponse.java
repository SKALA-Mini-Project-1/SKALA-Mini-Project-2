package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

public record PaymentOpsSummaryResponse(
        long totalPayments,
        long expiredPayments,
        double expiredRatePercent,
        long refundRequiredPayments,
        double refundRequiredRatePercent,
        long webhookDoneReceived,
        long duplicateWebhookDoneEstimated
) {
}
