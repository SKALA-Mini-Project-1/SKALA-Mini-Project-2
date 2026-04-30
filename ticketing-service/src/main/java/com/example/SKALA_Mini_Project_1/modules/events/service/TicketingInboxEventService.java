package com.example.SKALA_Mini_Project_1.modules.events.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.SKALA_Mini_Project_1.modules.events.domain.TicketingInboxEvent;
import com.example.SKALA_Mini_Project_1.modules.events.repository.TicketingInboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketingInboxEventService {

    private static final String STATUS_RECEIVED = "RECEIVED";
    private static final String STATUS_DUPLICATE = "DUPLICATE";

    private final TicketingInboxEventRepository ticketingInboxEventRepository;

    @Transactional
    public void recordIngress(
            String eventType,
            UUID bookingId,
            UUID paymentId,
            String pgOrderId,
            String pgPaymentKey
    ) {
        String dedupeKey = buildDedupeKey(eventType, bookingId, paymentId, pgOrderId, pgPaymentKey);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        TicketingInboxEvent existing = ticketingInboxEventRepository.findByDedupeKey(dedupeKey).orElse(null);
        if (existing != null) {
            existing.setStatus(STATUS_DUPLICATE);
            existing.setDuplicateCount((existing.getDuplicateCount() == null ? 0 : existing.getDuplicateCount()) + 1);
            existing.setLastSeenAt(now);
            return;
        }

        TicketingInboxEvent event = new TicketingInboxEvent();
        event.setId(UUID.randomUUID());
        event.setDedupeKey(dedupeKey);
        event.setEventType(eventType);
        event.setProducer("payment-service");
        event.setBookingId(bookingId);
        event.setPaymentId(paymentId);
        event.setAggregateId(bookingId == null ? null : bookingId.toString());
        event.setPgOrderId(pgOrderId);
        event.setPgPaymentKey(pgPaymentKey);
        event.setStatus(STATUS_RECEIVED);
        event.setDuplicateCount(0);
        event.setReceivedAt(now);
        event.setLastSeenAt(now);
        ticketingInboxEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public long countReceived() {
        return ticketingInboxEventRepository.countByStatus(STATUS_RECEIVED);
    }

    @Transactional(readOnly = true)
    public long countDuplicate() {
        return ticketingInboxEventRepository.countByStatus(STATUS_DUPLICATE);
    }

    @Transactional(readOnly = true)
    public List<TicketingInboxEvent> getRecentEvents() {
        return ticketingInboxEventRepository.findTop50ByOrderByLastSeenAtDesc();
    }

    private String buildDedupeKey(
            String eventType,
            UUID bookingId,
            UUID paymentId,
            String pgOrderId,
            String pgPaymentKey
    ) {
        return String.join(":",
                nullSafe(eventType),
                nullSafe(bookingId),
                nullSafe(paymentId),
                nullSafe(pgOrderId),
                nullSafe(pgPaymentKey));
    }

    /**
     * Kafka consumer용 dedupe 기록. eventId를 dedupe key로 사용한다.
     * 이미 처리된 이벤트면 false, 최초 수신이면 true를 반환한다.
     */
    @Transactional
    public boolean tryRecordKafkaEvent(
            UUID eventId,
            String eventType,
            UUID bookingId,
            UUID paymentId,
            String pgOrderId,
            String pgPaymentKey
    ) {
        String dedupeKey = "kafka:" + eventId.toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (ticketingInboxEventRepository.findByDedupeKey(dedupeKey).isPresent()) {
            return false;
        }

        TicketingInboxEvent event = new TicketingInboxEvent();
        event.setId(UUID.randomUUID());
        event.setDedupeKey(dedupeKey);
        event.setEventType(eventType);
        event.setProducer("payment-service");
        event.setBookingId(bookingId);
        event.setPaymentId(paymentId);
        event.setAggregateId(bookingId != null ? bookingId.toString() : null);
        event.setPgOrderId(pgOrderId);
        event.setPgPaymentKey(pgPaymentKey);
        event.setStatus(STATUS_RECEIVED);
        event.setDuplicateCount(0);
        event.setReceivedAt(now);
        event.setLastSeenAt(now);
        ticketingInboxEventRepository.save(event);
        return true;
    }

    private String nullSafe(Object value) {
        return value == null ? "null" : value.toString();
    }
}
