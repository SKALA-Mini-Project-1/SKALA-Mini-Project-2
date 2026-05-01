package com.example.SKALA_Mini_Project_1.kafka;

import com.example.SKALA_Mini_Project_1.modules.events.domain.TicketingOutbox;
import com.example.SKALA_Mini_Project_1.modules.events.repository.TicketingOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketingOutboxRelayScheduler {

    private final TicketingOutboxRepository ticketingOutboxRepository;
    private final TicketingKafkaProducer ticketingKafkaProducer;

    @Value("${ticketing.outbox.topic:ticketing.events.v1}")
    private String topic;

    @Value("${ticketing.outbox.max-retry:5}")
    private int maxRetry;

    @Scheduled(fixedDelayString = "${ticketing.outbox.relay.fixed-delay-ms:5000}")
    @Transactional
    public void relay() {
        List<TicketingOutbox> pending = ticketingOutboxRepository
                .findTop50ByPublishStatusOrderByCreatedAtAsc("PENDING");

        if (pending.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (TicketingOutbox ev : pending) {
            try {
                TicketingOutboxMessage msg = toMessage(ev);
                ticketingKafkaProducer.send(topic, ev.getOrderingKey(), msg);
                ev.setPublishStatus("PUBLISHED");
                ev.setPublishedAt(now);
                ev.setLastError(null);
            } catch (Exception e) {
                int retryCount = (ev.getRetryCount() == null ? 0 : ev.getRetryCount()) + 1;
                ev.setRetryCount(retryCount);
                ev.setLastError(e.getMessage());
                if (retryCount >= maxRetry) {
                    ev.setPublishStatus("FAILED");
                    log.error("Ticketing outbox event permanently failed. eventId={}, eventType={}", ev.getEventId(), ev.getEventType());
                } else {
                    log.warn("Ticketing outbox relay failed (attempt {}). eventId={}", retryCount, ev.getEventId());
                }
            }
        }
    }

    private TicketingOutboxMessage toMessage(TicketingOutbox ev) {
        return new TicketingOutboxMessage(
                ev.getEventId(),
                ev.getEventType(),
                ev.getEventVersion(),
                ev.getProducer(),
                ev.getAggregateType(),
                ev.getAggregateId(),
                ev.getOrderingKey(),
                ev.getBookingId(),
                ev.getPaymentId(),
                ev.getCorrelationId(),
                ev.getOccurredAt() != null ? ev.getOccurredAt().toString() : null,
                ev.getPayloadJson()
        );
    }
}
