package com.example.SKALA_Mini_Project_1.modules.reconciliation.dto;

public record ReconciliationTaskSummaryResponse(
        long requestedCount,
        long waitingManualApprovalCount,
        long completedCount
) {
}
