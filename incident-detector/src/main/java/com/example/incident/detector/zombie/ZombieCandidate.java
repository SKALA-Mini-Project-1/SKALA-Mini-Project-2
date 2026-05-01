package com.example.incident.detector.zombie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "detector_zombie_candidates")
@Getter
@Setter
public class ZombieCandidate {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID bookingId;

    private Long userId;
    private Long concertId;
    private Long scheduleId;

    @Column(length = 50)
    private String endedEventType;

    @Column(nullable = false)
    private OffsetDateTime endedAt;

    @Column(nullable = false)
    private OffsetDateTime checkAfterAt;

    @Column(nullable = false)
    private boolean checked = false;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
