package com.example.incident.detector.rules;

import com.example.incident.detector.kafka.TicketingEventMessage;
import com.example.incident.detector.zombie.ZombieCandidate;
import com.example.incident.detector.zombie.ZombieHoldCandidateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 탐지: booking.canceled / booking.expired 수신 후 유예 시간 경과 후 Redis hold key 잔존 (좀비 예약)
 *
 * 전략:
 * 1. booking.canceled / booking.expired 수신 → ZombieCandidate 등록 (checkAfterAt = now + gracePeriod)
 * 2. ZombieHoldCheckScheduler가 주기적으로 checkAfterAt 경과된 미확인 ZombieCandidate 스캔
 * 3. Redis에서 해당 bookingId의 hold key 잔존 여부 확인 → 잔존 시 incident 생성
 *
 * Redis key 패턴 (ticketing-service RedisKeyGenerator 참조):
 *   seatAccessKey  : "seat:access:{userId}:{concertId}:{scheduleId}"
 *   seatLockOwner  : "seat:lock:owner:{concertId}:{scheduleId}"
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZombieHoldRule {

    private final ZombieHoldCandidateRepository candidateRepository;
    private final ObjectMapper objectMapper;

    @Value("${detector.zombie.grace-period-seconds:60}")
    private int gracePeriodSeconds;

    public void registerCandidate(TicketingEventMessage event) {
        if (event.bookingId() == null) {
            log.warn("[zombie-hold] Event missing bookingId. type={}, eventId={}", event.eventType(), event.eventId());
            return;
        }

        if (candidateRepository.existsByBookingId(event.bookingId())) {
            log.debug("[zombie-hold] ZombieCandidate already registered. bookingId={}", event.bookingId());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ZombieCandidate candidate = new ZombieCandidate();
        candidate.setId(UUID.randomUUID());
        candidate.setBookingId(event.bookingId());
        candidate.setEndedEventType(event.eventType());
        candidate.setEndedAt(now);
        candidate.setCheckAfterAt(now.plusSeconds(gracePeriodSeconds));
        candidate.setChecked(false);
        candidate.setCreatedAt(now);

        Long userId = null;
        Long concertId = null;
        Long scheduleId = null;
        if (event.payloadJson() != null) {
            try {
                JsonNode payload = objectMapper.readTree(event.payloadJson());
                userId = payload.path("userId").isNull() ? null : payload.path("userId").asLong();
                concertId = payload.path("concertId").isNull() ? null : payload.path("concertId").asLong();
                scheduleId = payload.path("scheduleId").isNull() ? null : payload.path("scheduleId").asLong();
            } catch (Exception e) {
                log.warn("[zombie-hold] Failed to parse payloadJson. bookingId={}", event.bookingId());
            }
        }
        candidate.setUserId(userId);
        candidate.setConcertId(concertId);
        candidate.setScheduleId(scheduleId);

        candidateRepository.save(candidate);

        log.info("[zombie-hold] ZombieCandidate registered. bookingId={}, checkAfterAt={}",
                event.bookingId(), candidate.getCheckAfterAt());
    }
}
