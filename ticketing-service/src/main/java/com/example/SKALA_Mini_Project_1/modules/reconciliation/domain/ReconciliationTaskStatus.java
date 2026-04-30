package com.example.SKALA_Mini_Project_1.modules.reconciliation.domain;

public enum ReconciliationTaskStatus {
    REQUESTED,
    RUNNING,
    RETRY_WAIT,
    WAITING_MANUAL_APPROVAL,
    COMPLETED,
    FAILED_PERMANENT
}
