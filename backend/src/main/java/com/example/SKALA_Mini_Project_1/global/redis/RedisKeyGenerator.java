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

    public static String seatEntryKey(String entryToken) {
        return "seat:entry:" + entryToken;
    }

    public static String seatAccessKey(Long userId, Long concertId, Long scheduleId) {
        return "seat:access:user:" + userId + ":concert:" + concertId + ":schedule:" + scheduleId;
    }

    public static String seatAccessByScheduleKey(Long userId, Long scheduleId) {
        return "seat:access:user:" + userId + ":schedule:" + scheduleId;
    }

    public static String queueHeartbeatKey(Long concertId, Long scheduleId, String userId) {
        return "queue:heartbeat:concert:" + concertId + ":schedule:" + scheduleId + ":user:" + userId;
    }
}
