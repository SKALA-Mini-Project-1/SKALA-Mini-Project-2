package com.example.SKALA_Mini_Project_1.modules.events.service;

import com.example.SKALA_Mini_Project_1.modules.events.domain.TicketingOutbox;
import com.example.SKALA_Mini_Project_1.modules.events.repository.TicketingOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TicketingOutboxRecorder {

    private final TicketingOutboxRepository ticketingOutboxRepository;

    public void record(String eventType, UUID bookingId, UUID paymentId, String payloadJson, OffsetDateTime occurredAt) {
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
        outbox.setPayloadJson(payloadJson);
        outbox.setOccurredAt(occurredAt != null ? occurredAt : now);
        outbox.setCreatedAt(now);
        outbox.setPublishStatus("PENDING");
        outbox.setRetryCount(0);
        ticketingOutboxRepository.save(outbox);
    }
}
