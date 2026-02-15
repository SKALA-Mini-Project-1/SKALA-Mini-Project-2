package com.example.SKALA_Mini_Project_1.domain.Waiting.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.example.SKALA_Mini_Project_1.global.jwt.JwtUtil;

import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;



import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final int MAX_WEIGHT_MILLIS = 5000;

    public String tryEnterSeat(Long concertId, String userId) {

        String queueKey = getQueueKey(concertId);
        String enterKey = getEnterKey(concertId);

        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId);
        String allowedStr = redisTemplate.opsForValue().get(enterKey);

        if (rank == null || allowedStr == null) return null;

        long allowed = Long.parseLong(allowedStr);

        if (rank >= allowed) return null;

        // 대기열 제거
        redisTemplate.opsForZSet().remove(queueKey, userId);

        // 랜덤 EntryToken 생성
        String entryToken = java.util.UUID.randomUUID().toString();

        // 20초 유효 1회용 토큰 저장
        redisTemplate.opsForValue()
                .set("seat:entry:" + entryToken,
                        userId,
                        java.time.Duration.ofSeconds(180));

        return entryToken;
    }

    public long enterQueue(Long concertId, String userId, long fandomWeightMillis) {

        String key = getQueueKey(concertId);

        long now = System.currentTimeMillis();
        long weight = Math.min(fandomWeightMillis, MAX_WEIGHT_MILLIS);
        long jitter = ThreadLocalRandom.current().nextLong(0, 50);

        long score = now - weight + jitter;

        redisTemplate.opsForZSet().add(key, userId, score);

        Long rank = redisTemplate.opsForZSet().rank(key, userId);

        return rank != null ? rank : -1;
    }

    public long getRank(Long concertId, String userId) {
        String key = getQueueKey(concertId);
        Long rank = redisTemplate.opsForZSet().rank(key, userId);
        return rank != null ? rank : -1;
    }

    private String getQueueKey(Long concertId) {
        return "queue:concert:" + concertId;
    }

    private String getEnterKey(Long concertId) {
        return "queue:concert:" + concertId + ":enter";
    }
}