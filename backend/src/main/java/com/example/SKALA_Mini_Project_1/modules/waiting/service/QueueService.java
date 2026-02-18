package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import com.example.SKALA_Mini_Project_1.modules.users.User;
import com.example.SKALA_Mini_Project_1.modules.users.UserRepository;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.QueueStatusResponse;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.TicketingStartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final long MAX_SEAT_CAPACITY = 500;
    private static final int MAX_WEIGHT_MILLIS = 5000; // 팬점수 최대 보정치
    private static final Duration QUEUE_HEARTBEAT_TTL = Duration.ofMinutes(10);
    private static final Duration ENTRY_TOKEN_TTL = Duration.ofSeconds(180);
    private static final Duration SCHEDULE_VALIDATE_CACHE_TTL = Duration.ofMinutes(10);
    private static final int REDIS_RETRY_MAX_ATTEMPTS = 2;
    private static final long REDIS_RETRY_WAIT_MILLIS = 80L;

    private static final String ADMIT_AND_ISSUE_TOKEN_SCRIPT = """
            local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
            if not rank then
                return nil
            end

            local active = tonumber(redis.call('GET', KEYS[2]) or '0')
            local capacity = tonumber(ARGV[2])
            if not capacity then
                return nil
            end

            local available = capacity - active
            if available <= 0 then
                return nil
            end

            if rank < available then
                local removed = redis.call('ZREM', KEYS[1], ARGV[1])
                if removed == 1 then
                    redis.call('INCR', KEYS[2])
                    redis.call('SET', KEYS[3], ARGV[3], 'PX', ARGV[4])
                    return ARGV[5]
                end
            end

            return nil
            """;

    private static final String CONSUME_ENTRY_TOKEN_SCRIPT = """
            local value = redis.call('GET', KEYS[1])
            if not value then
                return nil
            end
            redis.call('DEL', KEYS[1])
            return value
            """;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public TicketingStartResponse startTicketing(Long concertId, Long scheduleId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        validateScheduleBelongsToConcert(concertId, scheduleId);

        int fanScore = user.getFanScore() == null ? 0 : user.getFanScore();
        long fanWeightMillis = Math.max(0, (long) fanScore * 100L);
        long rank = enterQueue(concertId, scheduleId, String.valueOf(userId), fanWeightMillis);
        return TicketingStartResponse.waiting(rank + 1);
    }

    public QueueStatusResponse getStatus(Long concertId, Long scheduleId, Long userId) {
        validateScheduleBelongsToConcert(concertId, scheduleId);

        String userKey = String.valueOf(userId);
        String queueKey = getQueueKey(concertId, scheduleId);

        try {
            Long rank = runWithRedisRetry(() -> redisTemplate.opsForZSet().rank(queueKey, userKey));
            if (rank == null) {
                return QueueStatusResponse.waiting(null);
            }

            refreshQueueHeartbeat(concertId, scheduleId, userKey);

            String entryToken = tryAdmitAndIssueEntryToken(concertId, scheduleId, userId);
            if (entryToken != null) {
                cleanupQueueIndexIfEmpty(queueKey);
                clearQueueHeartbeat(concertId, scheduleId, userKey);
                return QueueStatusResponse.enter(entryToken);
            }

            return QueueStatusResponse.waiting(rank + 1);
        } catch (RuntimeException e) {
            if (isRedisFailure(e)) {
                return QueueStatusResponse.waiting(null);
            }
            throw e;
        }
    }

    public boolean leaveQueue(Long concertId, Long scheduleId, Long userId) {
        validateScheduleBelongsToConcert(concertId, scheduleId);
        String queueKey = getQueueKey(concertId, scheduleId);
        Long removed = runWithRedisRetry(() ->
                redisTemplate.opsForZSet().remove(queueKey, String.valueOf(userId))
        );
        cleanupQueueIndexIfEmpty(queueKey);
        clearQueueHeartbeat(concertId, scheduleId, String.valueOf(userId));
        return removed != null && removed > 0;
    }

    public boolean hasQueueHeartbeat(Long concertId, Long scheduleId, String userId) {
        String key = RedisKeyGenerator.queueHeartbeatKey(concertId, scheduleId, userId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void clearQueueHeartbeat(Long concertId, Long scheduleId, String userId) {
        redisTemplate.delete(RedisKeyGenerator.queueHeartbeatKey(concertId, scheduleId, userId));
    }

    public String consumeEntryToken(String entryToken) {
        if (entryToken == null || entryToken.isBlank()) {
            return null;
        }

        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptText(CONSUME_ENTRY_TOKEN_SCRIPT);
        script.setResultType(String.class);

        String entryTokenKey = RedisKeyGenerator.seatEntryKey(entryToken);
        try {
            return runWithRedisRetry(() ->
                    redisTemplate.execute(script, List.of(entryTokenKey))
            );
        } catch (RuntimeException e) {
            if (isRedisFailure(e)) {
                return null;
            }
            throw e;
        }
    }

    private long enterQueue(Long concertId, Long scheduleId, String userId, long fandomWeightMillis) {
        String key = getQueueKey(concertId, scheduleId);
        String activeKey = getActiveKey(concertId, scheduleId);

        runWithRedisRetry(() -> redisTemplate.opsForValue().setIfAbsent(activeKey, "0"));
        runWithRedisRetry(() -> redisTemplate.opsForSet().add(RedisKeyGenerator.queueIndexKey(), key));
        runWithRedisRetry(() -> redisTemplate.opsForSet().add(RedisKeyGenerator.seatActiveIndexKey(), activeKey));

        long now = System.currentTimeMillis();
        long weight = Math.min(fandomWeightMillis, MAX_WEIGHT_MILLIS);
        long jitter = ThreadLocalRandom.current().nextLong(0, 50);
        long score = now - weight + jitter;

        runWithRedisRetry(() -> redisTemplate.opsForZSet().add(key, userId, score));
        refreshQueueHeartbeat(concertId, scheduleId, userId);

        Long rank = runWithRedisRetry(() -> redisTemplate.opsForZSet().rank(key, userId));
        return rank != null ? rank : -1;
    }

    private String tryAdmitAndIssueEntryToken(Long concertId, Long scheduleId, Long userId) {
        String userKey = String.valueOf(userId);
        String queueKey = getQueueKey(concertId, scheduleId);
        String activeKey = getActiveKey(concertId, scheduleId);

        String entryToken = UUID.randomUUID().toString();
        String entryTokenKey = RedisKeyGenerator.seatEntryKey(entryToken);
        String payload = userId + ":" + concertId + ":" + scheduleId;

        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptText(ADMIT_AND_ISSUE_TOKEN_SCRIPT);
        script.setResultType(String.class);

        return runWithRedisRetry(() -> redisTemplate.execute(
                script,
                List.of(queueKey, activeKey, entryTokenKey),
                userKey,
                String.valueOf(MAX_SEAT_CAPACITY),
                payload,
                String.valueOf(ENTRY_TOKEN_TTL.toMillis()),
                entryToken
        ));
    }

    private void validateScheduleBelongsToConcert(Long concertId, Long scheduleId) {
        if (concertId == null) {
            throw new IllegalArgumentException("concertId is required");
        }
        if (scheduleId == null) {
            throw new IllegalArgumentException("scheduleId is required");
        }

        String cacheKey = "queue:schedule:concert:" + concertId + ":schedule:" + scheduleId;
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
                return;
            }
        } catch (RuntimeException ignored) {
            // Redis 장애 시 DB 검증으로 폴백
        }

        String sql = "SELECT COUNT(1) FROM schedules WHERE id = ? AND concert_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, scheduleId, concertId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException(
                    "회차를 찾을 수 없습니다. concertId=" + concertId + ", scheduleId=" + scheduleId
            );
        }

        try {
            redisTemplate.opsForValue().set(cacheKey, "1", SCHEDULE_VALIDATE_CACHE_TTL);
        } catch (RuntimeException ignored) {
            // 캐시 기록 실패는 무시 (기능 영향 없음)
        }
    }

    private String getQueueKey(Long concertId, Long scheduleId) {
        return "queue:concert:" + concertId + ":schedule:" + scheduleId;
    }

    private String getActiveKey(Long concertId, Long scheduleId) {
        return "seat:active:concert:" + concertId + ":schedule:" + scheduleId;
    }

    private void refreshQueueHeartbeat(Long concertId, Long scheduleId, String userId) {
        runWithRedisRetry(() -> {
            redisTemplate.opsForValue().set(
                    RedisKeyGenerator.queueHeartbeatKey(concertId, scheduleId, userId),
                    "1",
                    QUEUE_HEARTBEAT_TTL
            );
            return null;
        });
    }

    public void cleanupQueueIndexIfEmpty(String queueKey) {
        Long size = runWithRedisRetry(() -> redisTemplate.opsForZSet().zCard(queueKey));
        if (size != null && size == 0L) {
            runWithRedisRetry(() -> redisTemplate.opsForSet().remove(RedisKeyGenerator.queueIndexKey(), queueKey));
        }
    }

    private <T> T runWithRedisRetry(Supplier<T> action) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= REDIS_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                last = e;
                if (!isRedisFailure(e) || attempt == REDIS_RETRY_MAX_ATTEMPTS) {
                    throw e;
                }
                try {
                    Thread.sleep(REDIS_RETRY_WAIT_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    private boolean isRedisFailure(RuntimeException e) {
        return e instanceof RedisConnectionFailureException || e instanceof DataAccessException;
    }
}
