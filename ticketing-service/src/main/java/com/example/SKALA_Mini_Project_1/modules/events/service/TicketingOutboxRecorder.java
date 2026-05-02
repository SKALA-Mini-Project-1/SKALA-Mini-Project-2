package com.example.SKALA_Mini_Project_1.modules.events.service;

import com.example.SKALA_Mini_Project_1.modules.events.domain.TicketingOutbox;
import com.example.SKALA_Mini_Project_1.modules.events.repository.TicketingOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TicketingOutboxRecorder {

    private final TicketingOutboxRepository ticketingOutboxRepository;
    private final ObjectMapper objectMapper;

    public void record(String eventType, UUID bookingId, UUID paymentId, String innerPayloadJson, OffsetDateTime occurredAt) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TicketingOutbox outbox = new TicketingOutbox();
        outbox.setEventId(UUID.randomUUID());
        outbox.setEventType(eventType);
        outbox.setEventVersion("v1");
        outbox.setProducer("ticketing-service");
        outbox.setAggregateType("booking");
        outbox.setAggregateId(bookingId != null ? bookingId.toString() : null);
        outbox.setOrderingKey(bookingId != null ? bookingId.toString() : null);
        outbox.setBookingId(bookingId);
        outbox.setPaymentId(paymentId);
        outbox.setCorrelationId(bookingId != null ? bookingId.toString() : null);
        outbox.setOccurredAt(occurredAt != null ? occurredAt : now);
        outbox.setCreatedAt(now);
        outbox.setPublishStatus("PENDING");
        outbox.setRetryCount(0);
        // payload_json stores the full message envelope so Debezium can send it directly to Kafka
        outbox.setPayloadJson(buildFullEnvelope(outbox, innerPayloadJson));
        ticketingOutboxRepository.save(outbox);
    }

    /**
     * Builds the full message envelope matching TicketingOutboxMessage / TicketingEventMessage.
     * Stored in payload_json so Debezium Outbox Event Router sends it verbatim to consumers.
     */
    private String buildFullEnvelope(TicketingOutbox outbox, String innerPayloadJson) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", outbox.getEventId());
        envelope.put("eventType", outbox.getEventType());
        envelope.put("eventVersion", outbox.getEventVersion());
        envelope.put("producer", outbox.getProducer());
        envelope.put("aggregateType", outbox.getAggregateType());
        envelope.put("aggregateId", outbox.getAggregateId());
        envelope.put("orderingKey", outbox.getOrderingKey());
        envelope.put("bookingId", outbox.getBookingId());
        envelope.put("paymentId", outbox.getPaymentId());
        envelope.put("correlationId", outbox.getCorrelationId());
        envelope.put("occurredAt", outbox.getOccurredAt() != null ? outbox.getOccurredAt().toString() : null);
        envelope.put("payloadJson", innerPayloadJson);

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ticketing event envelope", e);
        }
    }
}
