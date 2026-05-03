package com.example.SKALA_Mini_Project_1.modules.payments.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.Payment;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentEvent;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentEventRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentEventRecorder {

    private final PaymentEventRepository paymentEventRepository;
    private final ObjectMapper objectMapper;

    public void record(
            Payment payment,
            String eventType,
            String fromStatus,
            String toStatus,
            String extraInfo,
            String pgEventId
    ) {
        Map<String, Object> metadata = null;
        if (extraInfo != null && !extraInfo.isBlank()) {
            metadata = Map.of("extra", extraInfo);
        }
        recordWithMetadata(payment, eventType, fromStatus, toStatus, metadata, pgEventId);
    }

    public void recordWithMetadata(
            Payment payment,
            String eventType,
            String fromStatus,
            String toStatus,
            Map<String, Object> metadata,
            String pgEventId
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PaymentEvent ev = new PaymentEvent();
        ev.setEventId(UUID.randomUUID());
        ev.setPaymentId(payment.getId());
        ev.setBookingId(payment.getBookingId());
        ev.setEventType(eventType);
        ev.setEventVersion("v1");
        ev.setProducer("payment-service");
        ev.setAggregateType("payment");
        ev.setAggregateId(payment.getId().toString());
        ev.setOrderingKey(payment.getId().toString());
        ev.setCorrelationId(payment.getId().toString());
        ev.setCausationId(pgEventId == null || pgEventId.isBlank() ? ev.getEventId().toString() : pgEventId);
        ev.setTraceId(payment.getId().toString());
        ev.setFromStatus(fromStatus);
        ev.setToStatus(toStatus);
        ev.setIdempotencyKey(payment.getIdempotencyKey());
        ev.setPgEventId(pgEventId);
        ev.setOccurredAt(now);
        ev.setCreatedAt(now);
        ev.setPublishStatus("PENDING");
        ev.setRetryCount(0);
        // payload_json stores the full message envelope so Debezium can send it directly to Kafka
        ev.setPayloadJson(buildFullEnvelope(ev, payment, metadata));
        paymentEventRepository.save(ev);
    }

    /**
     * Builds the full message envelope matching PaymentOutboxMessage / PaymentEventMessage.
     * Stored in payload_json so Debezium Outbox Event Router sends it verbatim to consumers.
     */
    private String buildFullEnvelope(PaymentEvent ev, Payment payment, Map<String, Object> metadata) {
        Map<String, Object> innerPayload = new LinkedHashMap<>();
        innerPayload.put("bookingId", safe(payment.getBookingId()));
        innerPayload.put("amount", payment.getAmount() != null ? payment.getAmount() : 0);
        innerPayload.put("pgOrderId", safe(payment.getPgOrderId()));
        innerPayload.put("pgPaymentKey", safe(payment.getPgPaymentKey()));
        if (payment.getUserId() != null)     innerPayload.put("userId", payment.getUserId());
        if (payment.getScheduleId() != null) innerPayload.put("scheduleId", payment.getScheduleId());
        if (metadata != null && !metadata.isEmpty()) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getValue() != null) {
                    innerPayload.put(entry.getKey(), entry.getValue());
                }
            }
        }

        String innerJson;
        try {
            innerJson = objectMapper.writeValueAsString(innerPayload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize inner payment payload", e);
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", ev.getEventId());
        envelope.put("eventType", ev.getEventType());
        envelope.put("eventVersion", ev.getEventVersion());
        envelope.put("producer", ev.getProducer());
        envelope.put("aggregateType", ev.getAggregateType());
        envelope.put("aggregateId", ev.getAggregateId());
        envelope.put("orderingKey", ev.getOrderingKey());
        envelope.put("paymentId", ev.getPaymentId());
        envelope.put("bookingId", ev.getBookingId());
        envelope.put("pgOrderId", ev.getPgEventId());
        envelope.put("fromStatus", ev.getFromStatus());
        envelope.put("toStatus", ev.getToStatus());
        envelope.put("correlationId", ev.getCorrelationId());
        envelope.put("causationId", ev.getCausationId());
        envelope.put("traceId", ev.getTraceId());
        envelope.put("idempotencyKey", ev.getIdempotencyKey());
        envelope.put("occurredAt", ev.getOccurredAt() != null ? ev.getOccurredAt().toString() : null);
        envelope.put("payloadJson", innerJson);

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment event envelope", e);
        }
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }
}
