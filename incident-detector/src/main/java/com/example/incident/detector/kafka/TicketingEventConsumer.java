package com.example.incident.detector.kafka;

import com.example.incident.detector.inbox.DetectorInboxService;
import com.example.incident.detector.rules.GhostOrderRule;
import com.example.incident.detector.rules.ZombieHoldRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketingEventConsumer {

    private static final String TOPIC = "ticketing.events.v1";

    private final ObjectMapper objectMapper;
    private final DetectorInboxService inboxService;
    private final GhostOrderRule ghostOrderRule;
    private final ZombieHoldRule zombieHoldRule;

    @KafkaListener(topics = TOPIC, groupId = "incident-detector",
            containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void consume(String message) {
        TicketingEventMessage event;
        try {
            event = objectMapper.readValue(message, TicketingEventMessage.class);
        } catch (Exception e) {
            log.error("[detector] Failed to deserialize ticketing event. raw={}", message, e);
            return;
        }

        if (event.eventId() == null) {
            log.warn("[detector] Ticketing event missing eventId, skipping");
            return;
        }

        boolean isNew = inboxService.tryRecord(
                "kafka:" + event.eventId(),
                event.eventType(),
                TOPIC
        );
        if (!isNew) {
            log.debug("[detector] Duplicate ticketing event skipped. eventId={}, type={}", event.eventId(), event.eventType());
            return;
        }

        log.debug("[detector] Processing ticketing event. eventId={}, type={}", event.eventId(), event.eventType());

        String eventType = event.eventType();

        if ("booking.confirmed".equals(eventType)) {
            ghostOrderRule.onBookingConfirmed(event);
        } else if ("booking.canceled".equals(eventType) || "booking.expired".equals(eventType)) {
            zombieHoldRule.registerCandidate(event);
        }
    }
}
