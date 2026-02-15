package com.example.SKALA_Mini_Project_1.global.redis;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class RedisLockRepository {
    private final RedisTemplate<String, String> redisTemplate;

    public boolean lockSeat(Long concertId, Long seatId, String userId) {
        String key = RedisKeyGenerator.seatLockKey(concertId, seatId);

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, userId, Duration.ofMinutes(5));

        return Boolean.TRUE.equals(success);
    }

    public void unlockSeat(Long concertId, Long seatId) {
        String key = RedisKeyGenerator.seatLockKey(concertId, seatId);

        redisTemplate.delete(key);
    }

    public String getSeatOwner(Long concertId, Long seatId) {
        String key = RedisKeyGenerator.seatLockKey(concertId, seatId);

        return redisTemplate.opsForValue().get(key);
    }

    public Long getSeatLockTtlSeconds(Long concertId, Long seatId) {
        String key = RedisKeyGenerator.seatLockKey(concertId, seatId);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl <= 0) {
            return null;
        }
        return ttl;
    }

    public int countUserHeldSeats(Long concertId, String userId) {
        Set<String> keys = redisTemplate.keys("seat:concert:" + concertId + ":*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String key : keys) {
            String owner = redisTemplate.opsForValue().get(key);
            if (userId.equals(owner)) {
                count++;
            }
        }
        return count;
    }
}
