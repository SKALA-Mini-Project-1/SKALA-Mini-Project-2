package com.example.incident.detector.rules;

import com.example.incident.detector.correlation.PendingCorrelation;
import com.example.incident.detector.correlation.PendingCorrelationRepository;
import com.example.incident.detector.incident.IncidentWriteService;
import com.example.incident.detector.incident.dto.IncidentCreateCommand;
import com.example.incident.detector.kafka.PaymentEventMessage;
import com.example.incident.detector.kafka.TicketingEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * 탐지: PAYMENT_CONFIRMED 수신 후 N분 내 동일 bookingId의 booking.confirmed 미수신 (유령 주문)
 *
 * 전략:
 * 1. PAYMENT_CONFIRMED 수신 → PendingCorrelation(WAITING_BOOKING_CONFIRM, BOOKING_ID, bookingId) 등록
 * 2. booking.confirmed 수신 → 해당 PendingCorrelation resolved=true 마킹
 * 3. PendingCorrelationCheckScheduler가 deadline 초과된 미해결 건을 주기적으로 스캔 → incident 생성
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GhostOrderRule {

    public static final String CORRELATION_TYPE = "WAITING_BOOKING_CONFIRM";
    static final String KEY_TYPE = "BOOKING_ID";

    private final PendingCorrelationRepository correlationRepository;
    private final IncidentWriteService incidentWriteService;
    private final ObjectMapper objectMapper;

    @Value("${detector.ghost-order.threshold-minutes:5}")
    private int thresholdMinutes;

    public void onPaymentConfirmed(PaymentEventMessage event) {
        if (event.bookingId() == null) {
            log.warn("[ghost-order] PAYMENT_CONFIRMED missing bookingId. paymentId={}", event.paymentId());
            return;
        }

        String keyValue = event.bookingId().toString();

        boolean alreadyWaiting = correlationRepository
                .findByCorrelationTypeAndKeyTypeAndKeyValueAndResolvedFalse(CORRELATION_TYPE, KEY_TYPE, keyValue)
                .isPresent();

        if (alreadyWaiting) {
            log.debug("[ghost-order] Already waiting booking.confirmed for bookingId={}", event.bookingId());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        PendingCorrelation pending = new PendingCorrelation();
        pending.setId(UUID.randomUUID());
        pending.setCorrelationType(CORRELATION_TYPE);
        pending.setKeyType(KEY_TYPE);
        pending.setKeyValue(keyValue);
        pending.setTriggerEventType("PAYMENT_CONFIRMED");
        pending.setTriggeredAt(now);
        pending.setDeadlineAt(now.plusMinutes(thresholdMinutes));
        pending.setExtraJsonb(buildExtraJson(event));
        pending.setResolved(false);
        pending.setCreatedAt(now);
        correlationRepository.save(pending);

        log.info("[ghost-order] Registered pending correlation. bookingId={}, deadline={}",
                event.bookingId(), pending.getDeadlineAt());
    }

    public void onBookingConfirmed(TicketingEventMessage event) {
        if (event.bookingId() == null) {
            log.warn("[ghost-order] booking.confirmed missing bookingId. eventId={}", event.eventId());
            return;
        }

        String keyValue = event.bookingId().toString();
        int resolved = correlationRepository.resolveByTypeAndKey(CORRELATION_TYPE, KEY_TYPE, keyValue);

        if (resolved > 0) {
            log.info("[ghost-order] Correlation resolved. bookingId={}", event.bookingId());
        }
    }

    /**
     * PendingCorrelationCheckScheduler에서 호출: deadline 초과된 미해결 건을 incident로 전환
     */
    public void raiseIncidentFromExpired(PendingCorrelation pending) {
        UUID paymentId = extractUuidFromExtraJson(pending.getExtraJsonb(), "paymentId");
        Long userId    = extractLongFromExtraJson(pending.getExtraJsonb(), "userId");
        Long concertId = extractLongFromExtraJson(pending.getExtraJsonb(), "concertId");
        Long scheduleId = extractLongFromExtraJson(pending.getExtraJsonb(), "scheduleId");
        IncidentCreateCommand cmd = new IncidentCreateCommand(
                "GHOST_ORDER",
                pending.getKeyValue(),
                "high",
                "GHOST_ORDER_NO_BOOKING_CONFIRM",
                buildCurrentStateJson(pending),
                paymentId,
                UUID.fromString(pending.getKeyValue()),
                userId, concertId, scheduleId
        );
        incidentWriteService.createOrUpdate(cmd);
        log.info("[ghost-order] Incident created from expired correlation. bookingId={}", pending.getKeyValue());
    }

    private String buildExtraJson(PaymentEventMessage event) {
        try {
            // payloadJson에서 userId/concertId/scheduleId 추출 후 extraJsonb에 함께 저장
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
            java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("paymentId", String.valueOf(event.paymentId()));
            map.put("occurredAt", orEmpty(event.occurredAt()));
            if (userId != null)    map.put("userId", userId);
            if (concertId != null) map.put("concertId", concertId);
            if (scheduleId != null) map.put("scheduleId", scheduleId);
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String buildCurrentStateJson(PendingCorrelation pending) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "bookingId", pending.getKeyValue(),
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

    private UUID extractUuidFromExtraJson(String extraJson, String key) {
        if (extraJson == null || extraJson.isBlank()) return null;
        try {
            Map<String, Object> parsed = objectMapper.readValue(extraJson, new TypeReference<>() {});
            Object value = parsed.get(key);
            if (value == null) return null;
            return UUID.fromString(value.toString());
        } catch (Exception e) {
            log.debug("[ghost-order] Failed to parse extraJson for key={}", key, e);
            return null;
        }
    }

    private Long extractLongFromExtraJson(String extraJson, String key) {
        if (extraJson == null || extraJson.isBlank()) return null;
        try {
            Map<String, Object> parsed = objectMapper.readValue(extraJson, new TypeReference<>() {});
            Object value = parsed.get(key);
            if (value == null) return null;
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            log.debug("[ghost-order] Failed to parse extraJson for key={}, error={}", key, e.getMessage());
            return null;
        }
    }
}
