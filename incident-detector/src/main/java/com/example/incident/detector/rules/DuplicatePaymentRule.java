package com.example.incident.detector.rules;

import com.example.incident.detector.incident.IncidentWriteService;
import com.example.incident.detector.incident.dto.IncidentCreateCommand;
import com.example.incident.detector.kafka.PaymentEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * 탐지: 동일 bookingId로 PAYMENT_CONFIRMED가 2회 이상 수신되는 경우 (중복 결제)
 *
 * 전략: bookingId 기준으로 첫 PAYMENT_CONFIRMED 는 Redis에 기억만 하고,
 * 두 번째 이후 수신부터 (DUPLICATE_PAYMENT, bookingId) 키로 incident createOrUpdate 호출한다.
 * Inbox 중복 방지 덕분에 동일 eventId의 재처리는 이미 걸러진 상태.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DuplicatePaymentRule {

    private static final String FIRST_CONFIRM_KEY_PREFIX = "incident:duplicate-payment:first-confirmed:";

    private final IncidentWriteService incidentWriteService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${detector.rules.duplicate-payment.window-hours:24}")
    private long duplicateWindowHours;

    public void onPaymentConfirmed(PaymentEventMessage event) {
        if (event.bookingId() == null) {
            log.warn("[duplicate-payment] PAYMENT_CONFIRMED missing bookingId. paymentId={}", event.paymentId());
            return;
        }

        String incidentKey = event.bookingId().toString();
        String redisKey = FIRST_CONFIRM_KEY_PREFIX + incidentKey;
        String markerValue = event.eventId() != null ? event.eventId().toString() : String.valueOf(event.paymentId());

        Boolean firstSeen = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, markerValue, Duration.ofHours(duplicateWindowHours));
        if (Boolean.TRUE.equals(firstSeen)) {
            log.info("[duplicate-payment] First PAYMENT_CONFIRMED observed. bookingId={}, paymentId={}",
                    event.bookingId(), event.paymentId());
            return;
        }

        Long userId = null, concertId = null, scheduleId = null;
        if (event.payloadJson() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(event.payloadJson());
                if (!node.path("userId").isMissingNode() && !node.path("userId").isNull())
                    userId = node.path("userId").asLong();
                if (!node.path("concertId").isMissingNode() && !node.path("concertId").isNull())
                    concertId = node.path("concertId").asLong();
                if (!node.path("scheduleId").isMissingNode() && !node.path("scheduleId").isNull())
                    scheduleId = node.path("scheduleId").asLong();
            } catch (Exception ignored) {}
        }

        IncidentCreateCommand cmd = new IncidentCreateCommand(
                "DUPLICATE_PAYMENT",
                incidentKey,
                "high",
                "PAYMENT_CONFIRMED",
                buildStateJson(event),
                event.paymentId(),
                event.bookingId(),
                userId, concertId, scheduleId
        );

        incidentWriteService.createOrUpdate(cmd);
        log.info("[duplicate-payment] Incident triggered. bookingId={}, paymentId={}", event.bookingId(), event.paymentId());
    }

    private String buildStateJson(PaymentEventMessage event) {
        try {
            java.util.List<Map<String, String>> timeline = java.util.List.of(
                    Map.of("eventType", "PAYMENT_CONFIRMED_FIRST",
                            "occurredAt", orEmpty(event.occurredAt()),
                            "source", "payment",
                            "note", "first confirmation recorded in Redis"),
                    Map.of("eventType", "PAYMENT_CONFIRMED_DUPLICATE",
                            "occurredAt", orEmpty(event.occurredAt()),
                            "source", "payment",
                            "note", "duplicate confirmation detected for same bookingId")
            );
            java.util.LinkedHashMap<String, Object> state = new java.util.LinkedHashMap<>();
            state.put("paymentId", String.valueOf(event.paymentId()));
            state.put("bookingId", String.valueOf(event.bookingId()));
            state.put("fromStatus", orEmpty(event.fromStatus()));
            state.put("toStatus", orEmpty(event.toStatus()));
            state.put("occurredAt", orEmpty(event.occurredAt()));
            state.put("timeline", timeline);
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String orEmpty(String v) {
        return v != null ? v : "";
    }
}
