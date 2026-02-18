package com.example.SKALA_Mini_Project_1.global.redis;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RedisLockRepository {
    private static final String LOCK_WITH_LIMIT_SCRIPT = """
            local holdCount = tonumber(redis.call('SCARD', KEYS[2]) or '0')
            local maxHolds = tonumber(ARGV[2])
            if holdCount >= maxHolds then
                return -1
            end

            local ok = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[3])
            if not ok then
                return 0
            end

            redis.call('SADD', KEYS[2], ARGV[4])
            return 1
            """;
    private static final String UNLOCK_IF_OWNER_AND_REMOVE_HOLD_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('SREM', KEYS[2], ARGV[2])
                return 1
            end
            return 0
            """;
    private static final String DECR_ACTIVE_FLOOR_ZERO_SCRIPT = """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if not current or current <= 0 then
                redis.call('SET', KEYS[1], '0')
                return 0
            end
            return redis.call('DECR', KEYS[1])
            """;

    private final RedisTemplate<String, String> redisTemplate;

    public enum SeatLockWithLimitResult {
        LOCKED,
        LIMIT_EXCEEDED,
        ALREADY_LOCKED
    }

    public boolean lockSeat(Long concertId, Long scheduleId, Long seatId, String userId) {
        String key = RedisKeyGenerator.seatLockKey(concertId, scheduleId, seatId);

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, userId, Duration.ofMinutes(5));

        return Boolean.TRUE.equals(success);
    }

    public SeatLockWithLimitResult lockSeatWithLimit(
            Long concertId,
            Long scheduleId,
            Long seatId,
            String userId,
            int maxHoldCount
    ) {
        String seatKey = RedisKeyGenerator.seatLockKey(concertId, scheduleId, seatId);
        String holdSetKey = RedisKeyGenerator.seatUserHoldsKey(concertId, scheduleId, userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(LOCK_WITH_LIMIT_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(
                script,
                List.of(seatKey, holdSetKey),
                userId,
                String.valueOf(maxHoldCount),
                String.valueOf(Duration.ofMinutes(5).toMillis()),
                String.valueOf(seatId)
        );

        if (result != null && result == 1L) {
            return SeatLockWithLimitResult.LOCKED;
        }
        if (result != null && result == -1L) {
            return SeatLockWithLimitResult.LIMIT_EXCEEDED;
        }
        return SeatLockWithLimitResult.ALREADY_LOCKED;
    }

    public boolean unlockSeatIfOwner(Long concertId, Long scheduleId, Long seatId, String userId) {
        String key = RedisKeyGenerator.seatLockKey(concertId, scheduleId, seatId);
        String holdSetKey = RedisKeyGenerator.seatUserHoldsKey(concertId, scheduleId, userId);
        return unlockKeyIfOwnerAndRemoveHold(key, holdSetKey, userId, seatId);
    }

    private boolean unlockKeyIfOwnerAndRemoveHold(String key, String holdSetKey, String userId, Long seatId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(UNLOCK_IF_OWNER_AND_REMOVE_HOLD_SCRIPT);
        script.setResultType(Long.class);

        Long deleted = redisTemplate.execute(script, List.of(key, holdSetKey), userId, String.valueOf(seatId));
        return deleted != null && deleted > 0;
    }

    public String getSeatOwner(Long concertId, Long scheduleId, Long seatId) {
        String key = RedisKeyGenerator.seatLockKey(concertId, scheduleId, seatId);

        return redisTemplate.opsForValue().get(key);
    }

    public Long getSeatLockTtlSeconds(Long concertId, Long scheduleId, Long seatId) {
        String key = RedisKeyGenerator.seatLockKey(concertId, scheduleId, seatId);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl <= 0) {
            return null;
        }
        return ttl;
    }

    public int countUserHeldSeats(Long concertId, Long scheduleId, String userId) {
        String holdSetKey = RedisKeyGenerator.seatUserHoldsKey(concertId, scheduleId, userId);
        Long holdCount = redisTemplate.opsForSet().size(holdSetKey);
        if (holdCount != null && holdCount > 0) {
            return holdCount.intValue();
        }
        if (holdCount != null && holdCount == 0L) {
            // 기존 스캔 기반 데이터가 남아있는 경우를 위한 호환성 복구
            Set<String> keys = scanKeys("seat:concert:" + concertId + ":schedule:" + scheduleId + ":*");
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            int recovered = 0;
            for (String key : keys) {
                String owner = redisTemplate.opsForValue().get(key);
                if (!userId.equals(owner)) {
                    continue;
                }
                Long seatId = parseSeatId(key);
                if (seatId != null) {
                    redisTemplate.opsForSet().add(holdSetKey, String.valueOf(seatId));
                }
                recovered++;
            }
            return recovered;
        }
        return 0;
    }

    public int releaseUserHeldSeats(Long concertId, Long scheduleId, String userId) {
        String holdSetKey = RedisKeyGenerator.seatUserHoldsKey(concertId, scheduleId, userId);
        Set<String> seatIds = redisTemplate.opsForSet().members(holdSetKey);
        int released = 0;

        if (seatIds != null && !seatIds.isEmpty()) {
            for (String seatIdRaw : seatIds) {
                Long seatId;
                try {
                    seatId = Long.parseLong(seatIdRaw);
                } catch (NumberFormatException e) {
                    redisTemplate.opsForSet().remove(holdSetKey, seatIdRaw);
                    continue;
                }
                String key = RedisKeyGenerator.seatLockKey(concertId, scheduleId, seatId);
                if (unlockKeyIfOwnerAndRemoveHold(key, holdSetKey, userId, seatId)) {
                    released++;
                }
            }
            if (Boolean.FALSE.equals(redisTemplate.hasKey(holdSetKey))
                    || Long.valueOf(0L).equals(redisTemplate.opsForSet().size(holdSetKey))) {
                redisTemplate.delete(holdSetKey);
            }
            return released;
        }

        // 기존 스캔 기반 데이터가 남아있는 경우를 위한 호환성 해제
        Set<String> keys = scanKeys("seat:concert:" + concertId + ":schedule:" + scheduleId + ":*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        for (String key : keys) {
            Long seatId = parseSeatId(key);
            if (seatId == null) {
                continue;
            }
            if (unlockKeyIfOwnerAndRemoveHold(key, holdSetKey, userId, seatId)) {
                released++;
            }
        }
        return released;
    }

    public long decrementSeatActiveFloorZero(Long concertId, Long scheduleId) {
        String activeKey = RedisKeyGenerator.seatActiveKey(concertId, scheduleId);
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(DECR_ACTIVE_FLOOR_ZERO_SCRIPT);
        script.setResultType(Long.class);
        Long result = redisTemplate.execute(script, List.of(activeKey));
        return result == null ? 0L : result;
    }

    private Long parseSeatId(String seatKey) {
        String token = ":seatId:";
        int idx = seatKey.lastIndexOf(token);
        if (idx < 0) {
            return null;
        }
        String raw = seatKey.substring(idx + token.length());
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisConnection connection) -> {
            Set<String> keys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(1000).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }
}
