package com.example.incident.detector.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "incidents")
@Getter
@Setter
public class Incident {

    @Id
    private UUID incidentId;

    @Column(nullable = false, length = 50)
    private String incidentType;

    @Column(nullable = false, length = 255)
    private String incidentKey;

    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(nullable = false, length = 20)
    private String severity;

    private BigDecimal confidence;

    private UUID primaryPaymentId;
    private UUID primaryBookingId;
    private Long userId;
    private Long concertId;
    private Long scheduleId;

    @Column(nullable = false)
    private OffsetDateTime firstDetectedAt;

    @Column(nullable = false)
    private OffsetDateTime lastDetectedAt;

    private OffsetDateTime lastAnalyzedAt;

    @Column(nullable = false)
    private int latestAnalysisVersion = 0;

    @Column(nullable = false)
    private boolean needsHumanApproval = false;

    @JdbcTypeCode(SqlTypes.JSON)
    private String currentStateJsonb;

    @Column(length = 100)
    private String openReasonSignal;

    private OffsetDateTime resolvedAt;

    @Column(length = 100)
    private String resolvedBy;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;
}
