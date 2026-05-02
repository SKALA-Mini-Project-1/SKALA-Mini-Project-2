package com.example.incident.detector.rules;

import com.example.incident.detector.correlation.PendingCorrelation;
import com.example.incident.detector.correlation.PendingCorrelationRepository;
import com.example.incident.detector.incident.IncidentWriteService;
import com.example.incident.detector.incident.dto.IncidentCreateCommand;
import com.example.incident.detector.kafka.PaymentEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * 탐지: PAYMENT_PAID 또는 WEBHOOK_* 수신 후 N분 내 PAYMENT_CONFIRMED 미수신 (미확정 결제)
 *
 * 전략:
 * 1. PAYMENT_PAID / WEBHOOK_* 수신 → PendingCorrelation(WAITING_PAYMENT_CONFIRM, PAYMENT_ID, paymentId) 등록
 * 2. PAYMENT_CONFIRMED 수신 → 해당 PendingCorrelation resolved=true 마킹 (DuplicatePaymentRule 과 동일 이벤트)
 *    → GhostOrderRule.onPaymentConfirmed와 별개로, 이 rule에서는 PaymentEventConsumer가 명시 호출하지 않고
 *       PendingCorrelationCheckScheduler가 PAYMENT_CONFIRMED 수신 시 자동 resolve할 수 있도록
 *       PaymentEventConsumer에서 onPaymentConfirmed 를 추가 호출한다.
 * 3. PendingCorrelationCheckScheduler가 deadline 초과된 미해결 건 스캔 → incident 생성
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnconfirmedPaymentRule {

    public static final String CORRELATION_TYPE = "WAITING_PAYMENT_CONFIRM";
    static final String KEY_TYPE = "PAYMENT_ID";

    private final PendingCorrelationRepository correlationRepository;
    private final IncidentWriteService incidentWriteService;
    private final ObjectMapper objectMapper;

    @Value("${detector.unconfirmed.paid-threshold-minutes:2}")
    private int thresholdMinutes;

    public void onPaymentPaid(PaymentEventMessage event) {
        registerIfAbsent(event, "PAYMENT_PAID");
    }

    public void onWebhookReceived(PaymentEventMessage event) {
        registerIfAbsent(event, event.eventType());
    }

    /**
     * PAYMENT_CONFIRMED 수신 시 대기 중인 PendingCorrelation 해소
     * PaymentEventConsumer에서 PAYMENT_CONFIRMED 처리 시 함께 호출해야 함.
     */
    public void onPaymentConfirmed(PaymentEventMessage event) {
        if (event.paymentId() == null) return;
        String keyValue = event.paymentId().toString();
        int resolved = correlationRepository.resolveByTypeAndKey(CORRELATION_TYPE, KEY_TYPE, keyValue);
        if (resolved > 0) {
            log.info("[unconfirmed-payment] Correlation resolved. paymentId={}", event.paymentId());
        }
    }

    /**
     * PendingCorrelationCheckScheduler에서 호출: deadline 초과된 미해결 건을 incident로 전환
     */
    public void raiseIncidentFromExpired(PendingCorrelation pending) {
        IncidentCreateCommand cmd = new IncidentCreateCommand(
                "UNCONFIRMED_PAYMENT",
                pending.getKeyValue(),
                "medium",
                "UNCONFIRMED_PAYMENT_NO_CONFIRM",
                buildCurrentStateJson(pending),
                UUID.fromString(pending.getKeyValue()),
                null,
                null, null, null
        );
        incidentWriteService.createOrUpdate(cmd);
        log.info("[unconfirmed-payment] Incident created from expired correlation. paymentId={}", pending.getKeyValue());
    }

    private void registerIfAbsent(PaymentEventMessage event, String triggerEventType) {
        if (event.paymentId() == null) {
            log.warn("[unconfirmed-payment] Event missing paymentId. type={}", event.eventType());
            return;
        }

        String keyValue = event.paymentId().toString();

        boolean alreadyWaiting = correlationRepository
                .findByCorrelationTypeAndKeyTypeAndKeyValueAndResolvedFalse(CORRELATION_TYPE, KEY_TYPE, keyValue)
                .isPresent();

        if (alreadyWaiting) {
            log.debug("[unconfirmed-payment] Already waiting PAYMENT_CONFIRMED for paymentId={}", event.paymentId());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PendingCorrelation pending = new PendingCorrelation();
        pending.setId(UUID.randomUUID());
        pending.setCorrelationType(CORRELATION_TYPE);
        pending.setKeyType(KEY_TYPE);
        pending.setKeyValue(keyValue);
        pending.setTriggerEventType(triggerEventType);
        pending.setTriggeredAt(now);
        pending.setDeadlineAt(now.plusMinutes(thresholdMinutes));
        pending.setExtraJsonb(buildExtraJson(event));
        pending.setResolved(false);
        pending.setCreatedAt(now);
        correlationRepository.save(pending);

        log.info("[unconfirmed-payment] Registered pending correlation. paymentId={}, deadline={}",
                event.paymentId(), pending.getDeadlineAt());
    }

    private String buildExtraJson(PaymentEventMessage event) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "bookingId", String.valueOf(event.bookingId()),
                    "fromStatus", orEmpty(event.fromStatus()),
                    "toStatus", orEmpty(event.toStatus()),
                    "occurredAt", orEmpty(event.occurredAt())
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String buildCurrentStateJson(PendingCorrelation pending) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "paymentId", pending.getKeyValue(),
                    "triggeredAt", pending.getTriggeredAt().toString(),
                    "deadlineAt", pending.getDeadlineAt().toString(),
                    "extraJson", orEmpty(pending.getExtraJsonb())
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String orEmpty(String v) {
        return v != null ? v : "";
    }
}
