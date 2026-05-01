package com.example.SKALA_Mini_Project_1.kafka;

import com.example.SKALA_Mini_Project_1.modules.events.service.TicketingInboxEventService;
import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingCancelRequest;
import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingConfirmRequest;
import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingExpireRequest;
import com.example.SKALA_Mini_Project_1.modules.finalization.service.TicketingFinalizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final TicketingInboxEventService ticketingInboxEventService;
    private final TicketingFinalizationService ticketingFinalizationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "payment.events.v1",
            groupId = "ticketing-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(String message) {
        PaymentEventMessage event;
        try {
            event = objectMapper.readValue(message, PaymentEventMessage.class);
        } catch (Exception e) {
            log.error("Failed to deserialize payment event message. raw={}", message, e);
            return;
        }

        if (event.eventId() == null) {
            log.warn("Received payment event without eventId, skipping");
            return;
        }

        boolean isNew = ticketingInboxEventService.tryRecordKafkaEvent(
                event.eventId(),
                event.eventType(),
                event.bookingId(),
                event.paymentId(),
                event.pgOrderId(),
                null
        );
        if (!isNew) {
            log.debug("Duplicate payment event skipped. eventId={}, type={}", event.eventId(), event.eventType());
            return;
        }

        try {
            route(event);
        } catch (Exception e) {
            log.error("Failed to process payment event. eventId={}, type={}", event.eventId(), event.eventType(), e);
            throw e;
        }
    }

    private void route(PaymentEventMessage event) {
        if (event.bookingId() == null) {
            log.warn("Payment event missing bookingId, skipping. eventId={}, type={}", event.eventId(), event.eventType());
            return;
        }

        OffsetDateTime occurredAt = parseTime(event.occurredAt());

        switch (event.eventType()) {
            case "PAYMENT_CONFIRMED" -> {
                var request = new InternalBookingConfirmRequest(
                        event.paymentId(),
                        event.pgOrderId(),
                        null,
                        extractAmount(event.payloadJson()),
                        occurredAt,
                        event.bookingId()
                );
                ticketingFinalizationService.confirmBooking(request);
                log.info("Finalization triggered by PAYMENT_CONFIRMED. bookingId={}, paymentId={}",
                        event.bookingId(), event.paymentId());
            }
            case "PAYMENT_FAILED", "PAYMENT_CANCELED_BY_USER" -> {
                var request = new InternalBookingCancelRequest(
                        event.paymentId(),
                        event.pgOrderId(),
                        event.eventType(),
                        occurredAt,
                        event.bookingId()
                );
                ticketingFinalizationService.cancelBooking(request);
                log.info("Cancellation triggered by {}. bookingId={}", event.eventType(), event.bookingId());
            }
            case "PAYMENT_EXPIRED" -> {
                var request = new InternalBookingExpireRequest(
                        event.paymentId(),
                        event.pgOrderId(),
                        "PAYMENT_TIMEOUT",
                        occurredAt,
                        event.bookingId()
                );
                ticketingFinalizationService.expireBooking(request);
                log.info("Expiration triggered by PAYMENT_EXPIRED. bookingId={}", event.bookingId());
            }
            default -> log.debug("Unhandled event type in ticketing consumer: {}", event.eventType());
        }
    }

    private OffsetDateTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(timeStr);
        } catch (Exception e) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    private Long extractAmount(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            JsonNode amount = node.get("amount");
            return (amount != null && !amount.isNull()) ? amount.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

}
