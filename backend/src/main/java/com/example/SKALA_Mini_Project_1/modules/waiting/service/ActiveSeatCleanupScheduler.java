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

        String concertId = "1";

        // 현재 active 값
        String activeStr = redisTemplate.opsForValue()
                .get("seat:active:concert:" + concertId);

        long active = activeStr == null ? 0 : Long.parseLong(activeStr);

        // 실제 살아있는 entryToken 개수
        Set<String> keys = redisTemplate.keys("seat:entry:*");

        long realActive = keys == null ? 0 : keys.size();

        if (active != realActive) {
            redisTemplate.opsForValue().set(
                    "seat:active:concert:" + concertId,
                    String.valueOf(realActive)
            );

            System.out.println("ACTIVE 보정 → " + realActive);
        }
    }
}

