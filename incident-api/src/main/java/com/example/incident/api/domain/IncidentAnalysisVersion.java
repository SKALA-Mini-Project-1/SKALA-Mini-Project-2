package com.example.incident.api.domain;

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
@Table(name = "incident_analysis_versions")
@Getter
@Setter
public class IncidentAnalysisVersion {

    @Id
    private UUID analysisVersionId;

    @Column(nullable = false)
    private UUID incidentId;

    @Column(nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 20)
    private String analysisStatus;

    @Column(length = 100)
    private String inputSchemaVersion;

    @Column(length = 100)
    private String outputSchemaVersion;

    @Column(length = 50)
    private String triggerType;

    @Column(length = 100)
    private String requestedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    private String inputSnapshotJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
    private String outputJsonb;

    @Column(columnDefinition = "TEXT")
    private String summaryText;

    @Column(length = 100)
    private String llmModel;

    private Integer promptTokens;
    private Integer completionTokens;
    private Long latencyMs;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
