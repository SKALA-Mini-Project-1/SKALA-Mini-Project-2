package com.example.SKALA_Mini_Project_1.modules.payments.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    public void record(
            Payment payment,
            String eventType,
            String fromStatus,
            String toStatus,
            String extraInfo,
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
        ev.setPayloadJson(buildPayloadJson(payment, extraInfo));
        ev.setOccurredAt(now);
        ev.setCreatedAt(now);
        ev.setPublishStatus("PENDING");
        ev.setRetryCount(0);
        paymentEventRepository.save(ev);
    }

    private String buildPayloadJson(Payment payment, String extraInfo) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"bookingId\":\"").append(safe(payment.getBookingId())).append("\"");
        sb.append(",\"amount\":").append(payment.getAmount() != null ? payment.getAmount() : 0);
        sb.append(",\"pgOrderId\":\"").append(safe(payment.getPgOrderId())).append("\"");
        sb.append(",\"pgPaymentKey\":\"").append(safe(payment.getPgPaymentKey())).append("\"");
        if (extraInfo != null && !extraInfo.isBlank()) {
            sb.append(",\"extra\":\"").append(extraInfo.replace("\"", "\\\"")).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }
}
