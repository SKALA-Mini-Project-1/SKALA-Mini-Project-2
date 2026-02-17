package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ActiveSeatCleanupScheduler {

    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(fixedDelay = 10000) // 10초마다 실행
    public void syncActiveCount() {
        Set<String> activeKeys = redisTemplate.keys("seat:active:concert:*:schedule:*");
        if (activeKeys == null || activeKeys.isEmpty()) {
            return;
        }

        for (String activeKey : activeKeys) {
            Long concertId = parseLongBetween(activeKey, "seat:active:concert:", ":schedule:");
            Long scheduleId = parseLongAfter(activeKey, ":schedule:");
            if (concertId == null || scheduleId == null) {
                continue;
            }

            Set<String> accessKeys = redisTemplate.keys(
                    "seat:access:user:*:concert:" + concertId + ":schedule:" + scheduleId
            );
            long realActive = accessKeys == null ? 0L : accessKeys.size();
            redisTemplate.opsForValue().set(activeKey, String.valueOf(realActive));
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
