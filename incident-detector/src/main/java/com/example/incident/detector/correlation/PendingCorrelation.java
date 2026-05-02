package com.example.incident.detector.correlation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "detector_pending_correlations")
@Getter
@Setter
public class PendingCorrelation {

    @Id
    private UUID id;

    // WAITING_BOOKING_CONFIRM | WAITING_PAYMENT_CONFIRM
    @Column(nullable = false, length = 50)
    private String correlationType;

    // BOOKING_ID | PAYMENT_ID
    @Column(nullable = false, length = 30)
    private String keyType;

    @Column(nullable = false, length = 255)
    private String keyValue;

    @Column(length = 100)
    private String triggerEventType;

    @Column(nullable = false)
    private OffsetDateTime triggeredAt;

    @Column(nullable = false)
    private OffsetDateTime deadlineAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String extraJsonb;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
