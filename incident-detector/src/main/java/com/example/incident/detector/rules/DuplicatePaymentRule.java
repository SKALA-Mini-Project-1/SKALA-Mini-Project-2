package com.example.incident.detector.rules;

import com.example.incident.detector.incident.IncidentWriteService;
import com.example.incident.detector.incident.dto.IncidentCreateCommand;
import com.example.incident.detector.kafka.PaymentEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 탐지: 동일 bookingId로 PAYMENT_CONFIRMED가 2회 이상 수신되는 경우 (중복 결제)
 *
 * 전략: PAYMENT_CONFIRMED 수신마다 (DUPLICATE_PAYMENT, bookingId) 키로 incident createOrUpdate 호출.
 * 첫 번째 수신은 incident 생성, 두 번째 이후 수신은 last_detected_at 갱신 + severity 에스컬레이션.
 * Inbox 중복 방지 덕분에 동일 eventId의 재처리는 이미 걸러진 상태.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DuplicatePaymentRule {

    private final IncidentWriteService incidentWriteService;
    private final ObjectMapper objectMapper;

    public void onPaymentConfirmed(PaymentEventMessage event) {
        if (event.bookingId() == null) {
            log.warn("[duplicate-payment] PAYMENT_CONFIRMED missing bookingId. paymentId={}", event.paymentId());
            return;
        }

        String incidentKey = event.bookingId().toString();

        IncidentCreateCommand cmd = new IncidentCreateCommand(
                "DUPLICATE_PAYMENT",
                incidentKey,
                "high",
                "PAYMENT_CONFIRMED",
                buildStateJson(event),
                event.paymentId(),
                event.bookingId(),
                null, null, null
        );

        incidentWriteService.createOrUpdate(cmd);
        log.info("[duplicate-payment] Incident triggered. bookingId={}, paymentId={}", event.bookingId(), event.paymentId());
    }

    private String buildStateJson(PaymentEventMessage event) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "paymentId", String.valueOf(event.paymentId()),
                    "bookingId", String.valueOf(event.bookingId()),
                    "fromStatus", orEmpty(event.fromStatus()),
                    "toStatus", orEmpty(event.toStatus()),
                    "occurredAt", orEmpty(event.occurredAt())
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String orEmpty(String v) {
        return v != null ? v : "";
    }
}
