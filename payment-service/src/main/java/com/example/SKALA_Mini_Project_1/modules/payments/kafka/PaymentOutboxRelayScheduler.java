package com.example.SKALA_Mini_Project_1.modules.payments.kafka;

import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentEvent;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentEventRepository;
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
public class PaymentOutboxRelayScheduler {

    private final PaymentEventRepository paymentEventRepository;
    private final PaymentKafkaProducer paymentKafkaProducer;

    @Value("${payment.outbox.topic:payment.events.v1}")
    private String topic;

    @Value("${payment.outbox.max-retry:5}")
    private int maxRetry;

    @Scheduled(fixedDelayString = "${payment.outbox.relay.fixed-delay-ms:5000}")
    @Transactional
    public void relay() {
        List<PaymentEvent> pending = paymentEventRepository
                .findTop50ByPublishStatusOrderByCreatedAtAsc("PENDING");

        if (pending.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (PaymentEvent ev : pending) {
            try {
                PaymentOutboxMessage msg = toMessage(ev);
                paymentKafkaProducer.send(topic, ev.getOrderingKey(), msg);
                ev.setPublishStatus("PUBLISHED");
                ev.setPublishedAt(now);
                ev.setLastError(null);
            } catch (Exception e) {
                int retryCount = (ev.getRetryCount() == null ? 0 : ev.getRetryCount()) + 1;
                ev.setRetryCount(retryCount);
                ev.setLastError(e.getMessage());
                if (retryCount >= maxRetry) {
                    ev.setPublishStatus("FAILED");
                    log.error("Payment outbox event permanently failed. eventId={}, eventType={}", ev.getEventId(), ev.getEventType());
                } else {
                    log.warn("Payment outbox relay failed (attempt {}). eventId={}", retryCount, ev.getEventId());
                }
            }
        }
    }

    private PaymentOutboxMessage toMessage(PaymentEvent ev) {
        return new PaymentOutboxMessage(
                ev.getEventId(),
                ev.getEventType(),
                ev.getEventVersion(),
                ev.getProducer(),
                ev.getAggregateType(),
                ev.getAggregateId(),
                ev.getOrderingKey(),
                ev.getPaymentId(),
                ev.getBookingId(),
                ev.getPgEventId(),
                ev.getFromStatus(),
                ev.getToStatus(),
                ev.getCorrelationId(),
                ev.getCausationId(),
                ev.getTraceId(),
                ev.getIdempotencyKey(),
                ev.getOccurredAt() != null ? ev.getOccurredAt().toString() : null,
                ev.getPayloadJson()
        );
    }
}
