package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActiveSeatCleanupScheduler {

    private static final String SCHEDULER_LOCK_KEY = "active:scheduler:sync:lock";
    private static final Duration SCHEDULER_LOCK_TTL = Duration.ofSeconds(30);
    private static final int ACTIVE_INDEX_SCAN_COUNT = 200;
    private static final int ACCESS_INDEX_SCAN_COUNT = 300;

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

    @Scheduled(fixedDelayString = "${queue.scheduler.active-seat-sync.fixed-delay-ms:10000}")
    public void syncActiveCount() {
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(SCHEDULER_LOCK_KEY, lockToken, SCHEDULER_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        try {
            syncActiveCountInternal(lockToken);
        } finally {
            releaseLockIfOwner(lockToken);
        }
    }

    private void syncActiveCountInternal(String lockToken) {
        Cursor<String> activeKeyCursor = redisTemplate.opsForSet().scan(
                RedisKeyGenerator.seatActiveIndexKey(),
                ScanOptions.scanOptions().count(ACTIVE_INDEX_SCAN_COUNT).build()
        );

        List<String> activeKeys = new ArrayList<>();
        try (activeKeyCursor) {
            while (activeKeyCursor.hasNext()) {
                activeKeys.add(activeKeyCursor.next());
            }
        } catch (Exception e) {
            return;
        }

        for (String activeKey : activeKeys) {
            if (!renewLockIfOwner(lockToken)) {
                return;
            }

            if (!Boolean.TRUE.equals(redisTemplate.hasKey(activeKey))) {
                redisTemplate.opsForSet().remove(RedisKeyGenerator.seatActiveIndexKey(), activeKey);
                continue;
            }

            Long concertId = parseLongBetween(activeKey, "seat:active:concert:", ":schedule:");
            Long scheduleId = parseLongAfter(activeKey, ":schedule:");
            if (concertId == null || scheduleId == null) {
                continue;
            }

            String accessIndexKey = RedisKeyGenerator.seatAccessIndexKey(concertId, scheduleId);
            long realActive = 0L;
            List<String> staleUserIds = new ArrayList<>();

            Cursor<String> memberCursor = redisTemplate.opsForSet().scan(
                    accessIndexKey,
                    ScanOptions.scanOptions().count(ACCESS_INDEX_SCAN_COUNT).build()
            );
            try (memberCursor) {
                while (memberCursor.hasNext()) {
                    String userIdRaw = memberCursor.next();
                    Long userId;
                    try {
                        userId = Long.parseLong(userIdRaw);
                    } catch (NumberFormatException e) {
                        staleUserIds.add(userIdRaw);
                        continue;
                    }

                    String accessKey = RedisKeyGenerator.seatAccessKey(userId, concertId, scheduleId);
                    String accessByScheduleKey = RedisKeyGenerator.seatAccessByScheduleKey(userId, scheduleId);

                    boolean alive = Boolean.TRUE.equals(redisTemplate.hasKey(accessKey))
                            || Boolean.TRUE.equals(redisTemplate.hasKey(accessByScheduleKey));

                    if (!alive) {
                        staleUserIds.add(userIdRaw);
                        continue;
                    }
                    realActive++;
                }
            } catch (Exception e) {
                continue;
            }

            if (!staleUserIds.isEmpty()) {
                redisTemplate.opsForSet().remove(accessIndexKey, staleUserIds.toArray());
            }
            redisTemplate.opsForValue().set(activeKey, String.valueOf(realActive));
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
