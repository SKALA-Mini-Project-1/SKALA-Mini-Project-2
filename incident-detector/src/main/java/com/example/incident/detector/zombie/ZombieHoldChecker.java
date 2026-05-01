package com.example.incident.detector.zombie;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Redis에서 bookingId와 연관된 좌석 hold key 잔존 여부를 확인.
 *
 * ticketing-service RedisKeyGenerator 패턴:
 *   seatAccessKey : "seat:access:{userId}:{concertId}:{scheduleId}"
 *   seatLockOwner : "seat:lock:owner:{concertId}:{scheduleId}"
 *
 * userId/concertId/scheduleId 는 ZombieCandidate에 저장되어 있어야 하며,
 * null인 경우 seatLockOwner 패턴으로 대체 조회한다 (scheduleId 기준).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZombieHoldChecker {

    private final StringRedisTemplate redisTemplate;

    /**
     * 해당 candidate에 대해 Redis hold key가 아직 살아있는지 확인.
     * @return true → 좀비 hold 잔존, false → 정상 해제됨
     */
    public boolean isHoldStillPresent(ZombieCandidate candidate) {
        // userId, concertId, scheduleId가 모두 있으면 seatAccessKey 조회
        if (candidate.getUserId() != null
                && candidate.getConcertId() != null
                && candidate.getScheduleId() != null) {
            String seatAccessKey = seatAccessKey(
                    candidate.getUserId(),
                    candidate.getConcertId(),
                    candidate.getScheduleId()
            );
            Boolean exists = redisTemplate.hasKey(seatAccessKey);
            log.debug("[zombie-checker] seatAccessKey check. key={}, exists={}", seatAccessKey, exists);
            return Boolean.TRUE.equals(exists);
        }

        // scheduleId만 있는 경우 seatLockOwner 조회 후 value가 bookingId인지 확인
        if (candidate.getConcertId() != null && candidate.getScheduleId() != null) {
            String seatLockOwnerKey = seatLockOwnerKey(candidate.getConcertId(), candidate.getScheduleId());
            String lockOwner = redisTemplate.opsForValue().get(seatLockOwnerKey);
            boolean match = candidate.getBookingId().toString().equals(lockOwner);
            log.debug("[zombie-checker] seatLockOwner check. key={}, lockOwner={}, bookingIdMatch={}",
                    seatLockOwnerKey, lockOwner, match);
            return match;
        }

        // 필요한 정보가 없으면 확인 불가 → 보수적으로 false (탐지하지 않음)
        log.warn("[zombie-checker] Insufficient info to check Redis hold. bookingId={}, userId={}, concertId={}, scheduleId={}",
                candidate.getBookingId(), candidate.getUserId(), candidate.getConcertId(), candidate.getScheduleId());
        return false;
    }

    private String seatAccessKey(Long userId, Long concertId, Long scheduleId) {
        return "seat:access:" + userId + ":" + concertId + ":" + scheduleId;
    }

    private String seatLockOwnerKey(Long concertId, Long scheduleId) {
        return "seat:lock:owner:" + concertId + ":" + scheduleId;
    }
}
