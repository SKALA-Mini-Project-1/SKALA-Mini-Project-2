package com.example.SKALA_Mini_Project_1.modules.reconciliation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reconciliation_tasks")
@Getter
@Setter
public class ReconciliationTask {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "booking_id", nullable = false, columnDefinition = "uuid")
    private UUID bookingId;

    @Column(name = "payment_id", columnDefinition = "uuid")
    private UUID paymentId;

    @Column(name = "mismatch_type", nullable = false, length = 80)
    private String mismatchType;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "booking_status", length = 40)
    private String bookingStatus;

    @Column(name = "reason_code", length = 120)
    private String reasonCode;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "concert_id")
    private Long concertId;

    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "seat_ids_csv", length = 1000)
    private String seatIdsCsv;

    @Column(name = "pg_order_id", length = 120)
    private String pgOrderId;

    @Column(name = "pg_payment_key", length = 120)
    private String pgPaymentKey;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "requested_at")
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
