package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class QueueScheduler {
    private final RedisTemplate<String, String> redisTemplate;
    private final QueueService queueService;

    @Scheduled(fixedDelay = 10000)
    public void cleanupStaleQueueMembers() {
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent("queue:scheduler:cleanup:lock", "1", Duration.ofSeconds(5));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        Set<String> queueKeys = redisTemplate.opsForSet().members(RedisKeyGenerator.queueIndexKey());
        if (queueKeys == null || queueKeys.isEmpty()) {
            return;
        }

        for (String queueKey : queueKeys) {
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
