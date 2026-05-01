package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QueueScheduler {
    private static final String SCHEDULER_LOCK_KEY = "queue:scheduler:cleanup:lock";
    private static final Duration SCHEDULER_LOCK_TTL = Duration.ofSeconds(30);
    private static final String RELEASE_LOCK_IF_OWNER_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """;
    private static final String RENEW_LOCK_IF_OWNER_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """;

    private final RedisTemplate<String, String> redisTemplate;
    private final QueueService queueService;

    @Scheduled(fixedDelayString = "${queue.scheduler.cleanup.fixed-delay-ms:10000}")
    public void cleanupStaleQueueMembers() {
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(SCHEDULER_LOCK_KEY, lockToken, SCHEDULER_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            Set<String> queueKeys = redisTemplate.opsForSet().members(RedisKeyGenerator.queueIndexKey());
            if (queueKeys == null || queueKeys.isEmpty()) {
                return;
            }

            for (String queueKey : queueKeys) {
                if (!renewLockIfOwner(lockToken)) {
                    return;
                }

                Long concertId = parseLongBetween(queueKey, "queue:concert:", ":schedule:");
                Long scheduleId = parseLongAfter(queueKey, ":schedule:");
                if (concertId == null || scheduleId == null) {
                    continue;
                }

                Set<String> members = redisTemplate.opsForZSet().range(queueKey, 0, -1);
                if (members == null || members.isEmpty()) {
                    queueService.cleanupQueueIndexIfEmpty(queueKey);
                    continue;
                }

                for (String userId : members) {
                    if (queueService.hasQueueHeartbeat(concertId, scheduleId, userId)) {
                        continue;
                    }
                    redisTemplate.opsForZSet().remove(queueKey, userId);
                }

                queueService.cleanupQueueIndexIfEmpty(queueKey);
            }
        } finally {
            releaseLockIfOwner(lockToken);
        }
    }

    private boolean renewLockIfOwner(String lockToken) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RENEW_LOCK_IF_OWNER_SCRIPT);
        script.setResultType(Long.class);
        Long renewed = redisTemplate.execute(
                script,
                List.of(SCHEDULER_LOCK_KEY),
                lockToken,
                String.valueOf(SCHEDULER_LOCK_TTL.toMillis())
        );
        return renewed != null && renewed > 0;
    }

    private void releaseLockIfOwner(String lockToken) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RELEASE_LOCK_IF_OWNER_SCRIPT);
        script.setResultType(Long.class);
        redisTemplate.execute(script, List.of(SCHEDULER_LOCK_KEY), lockToken);
    }

    private Long parseLongBetween(String value, String prefix, String suffix) {
        int start = value.indexOf(prefix);
        int end = value.indexOf(suffix);
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        String raw = value.substring(start + prefix.length(), end);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLongAfter(String value, String token) {
        int start = value.indexOf(token);
        if (start < 0) {
            return null;
        }
        String raw = value.substring(start + token.length());
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
