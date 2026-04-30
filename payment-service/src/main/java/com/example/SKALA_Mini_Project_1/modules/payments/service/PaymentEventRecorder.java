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
            String payloadJson,
            String pgEventId
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PaymentEvent ev = new PaymentEvent();
        ev.setEventId(UUID.randomUUID());
        ev.setPaymentId(payment.getId());
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
        ev.setPayloadJson(payloadJson);
        ev.setOccurredAt(now);
        ev.setCreatedAt(now);
        paymentEventRepository.save(ev);
    }
}
