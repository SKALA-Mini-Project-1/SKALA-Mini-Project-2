package com.example.SKALA_Mini_Project_1.modules.events.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ticketing_inbox_events", schema = "ticketing")
@Getter
@Setter
public class TicketingInboxEvent {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "dedupe_key", nullable = false, unique = true, length = 255)
    private String dedupeKey;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "producer", nullable = false, length = 60)
    private String producer;

    @Column(name = "booking_id", columnDefinition = "uuid")
    private UUID bookingId;

    @Column(name = "payment_id", columnDefinition = "uuid")
    private UUID paymentId;

    @Column(name = "aggregate_id", length = 120)
    private String aggregateId;

    @Column(name = "pg_order_id", length = 120)
    private String pgOrderId;

    @Column(name = "pg_payment_key", length = 120)
    private String pgPaymentKey;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "duplicate_count")
    private Integer duplicateCount;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;
}
