package com.example.incident.detector.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "detector_inbox_events")
@Getter
@Setter
public class DetectorInboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String dedupeKey;

    private String eventType;
    private String sourceTopic;

    @Column(nullable = false)
    private OffsetDateTime receivedAt;

    @Column(nullable = false)
    private int duplicateCount = 0;
}
