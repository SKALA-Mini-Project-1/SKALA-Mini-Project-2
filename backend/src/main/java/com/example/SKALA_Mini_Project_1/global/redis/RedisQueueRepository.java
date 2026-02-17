package com.example.SKALA_Mini_Project_1.global.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RedisQueueRepository {
    private final RedisTemplate<String, String> redisTemplate;

    public void addToQueue(Long concertId, Long scheduleId, String userId) {

        String key = RedisKeyGenerator.queueKey(concertId, scheduleId);
        long timestamp = System.currentTimeMillis();

        redisTemplate.opsForZSet()
                .add(key, userId, timestamp);
    }

    public Long getRank(Long concertId, Long scheduleId, String userId) {

        String key = RedisKeyGenerator.queueKey(concertId, scheduleId);

        return redisTemplate.opsForZSet()
                .rank(key, userId);
    }

    public void removeFromQueue(Long concertId, Long scheduleId, String userId) {

        String key = RedisKeyGenerator.queueKey(concertId, scheduleId);

        redisTemplate.opsForZSet()
                .remove(key, userId);
    }
}
