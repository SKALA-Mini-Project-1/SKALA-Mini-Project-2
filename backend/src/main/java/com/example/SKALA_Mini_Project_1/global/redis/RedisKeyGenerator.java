package com.example.SKALA_Mini_Project_1.global.redis;

public class RedisKeyGenerator {
    public static String queueKey(Long concertId, Long scheduleId) {
        return "queue:concert:" + concertId + ":schedule:" + scheduleId;
    }

    public static String seatLockKey(Long concertId, Long scheduleId, Long seatId) {
        return "seat:concert:" + concertId + ":schedule:" + scheduleId + ":seatId:" + seatId;
    }

    public static String seatActiveKey(Long concertId, Long scheduleId) {
        return "seat:active:concert:" + concertId + ":schedule:" + scheduleId;
    }
}
