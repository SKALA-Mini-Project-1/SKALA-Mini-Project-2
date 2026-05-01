package com.example.incident.detector.kafka;

import com.example.incident.detector.inbox.DetectorInboxService;
import com.example.incident.detector.rules.DuplicatePaymentRule;
import com.example.incident.detector.rules.GhostOrderRule;
import com.example.incident.detector.rules.UnconfirmedPaymentRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private static final String TOPIC = "payment.events.v1";

    private final ObjectMapper objectMapper;
    private final DetectorInboxService inboxService;
    private final DuplicatePaymentRule duplicatePaymentRule;
    private final GhostOrderRule ghostOrderRule;
    private final UnconfirmedPaymentRule unconfirmedPaymentRule;

    @KafkaListener(topics = TOPIC, groupId = "incident-detector",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void consume(String message) {
        PaymentEventMessage event;
        try {
            event = objectMapper.readValue(message, PaymentEventMessage.class);
        } catch (Exception e) {
            log.error("[detector] Failed to deserialize payment event. raw={}", message, e);
            return;
        }

        if (event.eventId() == null) {
            log.warn("[detector] Payment event missing eventId, skipping");
            return;
        }

        boolean isNew = inboxService.tryRecord(
                "kafka:" + event.eventId(),
                event.eventType(),
                TOPIC
        );
        if (!isNew) {
            log.debug("[detector] Duplicate payment event skipped. eventId={}, type={}", event.eventId(), event.eventType());
            return;
        }

        log.debug("[detector] Processing payment event. eventId={}, type={}", event.eventId(), event.eventType());

        String eventType = event.eventType();

        if ("PAYMENT_CONFIRMED".equals(eventType)) {
            duplicatePaymentRule.onPaymentConfirmed(event);
            ghostOrderRule.onPaymentConfirmed(event);
            unconfirmedPaymentRule.onPaymentConfirmed(event);
        } else if ("PAYMENT_PAID".equals(eventType)) {
            unconfirmedPaymentRule.onPaymentPaid(event);
        } else if (eventType != null && eventType.startsWith("WEBHOOK_")) {
            unconfirmedPaymentRule.onWebhookReceived(event);
        }
    }
}
