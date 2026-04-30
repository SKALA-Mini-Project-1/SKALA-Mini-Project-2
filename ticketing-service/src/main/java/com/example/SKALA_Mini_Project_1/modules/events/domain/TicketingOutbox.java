package com.example.SKALA_Mini_Project_1.modules.events.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticketing_outbox")
@Getter
@Setter
public class TicketingOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "event_version", nullable = false, length = 20)
    private String eventVersion = "v1";

    @Column(name = "producer", nullable = false, length = 60)
    private String producer = "ticketing-service";

    @Column(name = "aggregate_type", nullable = false, length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 100)
    private String aggregateId;

    @Column(name = "ordering_key", length = 100)
    private String orderingKey;

    @Column(name = "booking_id", columnDefinition = "uuid")
    private UUID bookingId;

    @Column(name = "payment_id", columnDefinition = "uuid")
    private UUID paymentId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "payload_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payloadJson;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "publish_status", length = 20, nullable = false)
    private String publishStatus = "PENDING";

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}
