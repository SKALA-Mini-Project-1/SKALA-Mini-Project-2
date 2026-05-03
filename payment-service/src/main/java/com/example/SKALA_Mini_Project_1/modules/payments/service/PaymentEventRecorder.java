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
        ev.setPayloadJson(buildPayloadJson(payment, metadata));
        ev.setOccurredAt(now);
        ev.setCreatedAt(now);
        ev.setPublishStatus("PENDING");
        ev.setRetryCount(0);
        paymentEventRepository.save(ev);
    }

    private String buildPayloadJson(Payment payment, Map<String, Object> metadata) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", safe(payment.getBookingId()));
        payload.put("amount", payment.getAmount() != null ? payment.getAmount() : 0);
        payload.put("pgOrderId", safe(payment.getPgOrderId()));
        payload.put("pgPaymentKey", safe(payment.getPgPaymentKey()));
        if (payment.getUserId() != null)    payload.put("userId", payment.getUserId());
        if (payment.getScheduleId() != null) payload.put("scheduleId", payment.getScheduleId());
        if (metadata != null && !metadata.isEmpty()) {
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                if (entry.getValue() != null) {
                    payload.put(entry.getKey(), entry.getValue());
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment event payload", e);
        }
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }
}
