package com.example.incident.detector.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "incident_status_history")
@Getter
@Setter
public class IncidentStatusHistory {

    @Id
    private UUID historyId;

    @Column(nullable = false)
    private UUID incidentId;

    @Column(length = 30)
    private String fromStatus;

    @Column(nullable = false, length = 30)
    private String toStatus;

    @Column(length = 100)
    private String changedBy;

    private String changeReason;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
