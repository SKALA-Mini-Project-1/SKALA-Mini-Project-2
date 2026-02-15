package com.example.SKALA_Mini_Project_1.global.redis;

public class RedisKeyGenerator {
    public static String queueKey(Long concertId) {
        return "queue:concert:" + concertId;
    }

    public static String seatLockKey(Long concertId, Long seatId) {
        return "seat:concert:" + concertId + ":seatId:" + seatId;
    }
}
