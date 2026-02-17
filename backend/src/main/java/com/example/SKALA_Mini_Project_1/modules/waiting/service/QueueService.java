package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import com.example.SKALA_Mini_Project_1.modules.users.UserRepository;
import com.example.SKALA_Mini_Project_1.modules.users.User;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.QueueStatusResponse;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.TicketingStartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final long MAX_SEAT_CAPACITY = 500;
    private static final int MAX_WEIGHT_MILLIS = 5000; // 팬점수 최대 보정치

    public TicketingStartResponse startTicketing(Long concertId, Long scheduleId, Long userId) {

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        validateScheduleBelongsToConcert(concertId, scheduleId);
        // fanScore가 높을수록 더 큰 가중치(ms)를 부여해 score를 낮춰 앞순번을 유도한다.
        int fanScore = user.getFanScore() == null ? 0 : user.getFanScore();
        long fanWeightMillis = Math.max(0, (long) fanScore * 100L);
        long rank = enterQueue(concertId, scheduleId, String.valueOf(userId), fanWeightMillis);
        return TicketingStartResponse.waiting(rank + 1);
    }

    private long enterQueue(Long concertId, Long scheduleId, String userId, long fandomWeightMillis) {

        String key = getQueueKey(concertId, scheduleId);
        redisTemplate.opsForValue().setIfAbsent(getActiveKey(concertId, scheduleId), "0");

        long now = System.currentTimeMillis();
        // 가중치는 fanScore*100 기반 입력값을 상한(MAX_WEIGHT_MILLIS)으로 제한해 과도한 우선권을 방지한다.
        long weight = Math.min(fandomWeightMillis, MAX_WEIGHT_MILLIS);
        // 동점 방지를 위한 소량의 랜덤 오프셋
        long jitter = ThreadLocalRandom.current().nextLong(0, 50);

        // score가 낮을수록 앞순번이므로, fanScore 가중치를 시간값에서 차감한다.
        long score = now - weight + jitter;

        redisTemplate.opsForZSet().add(key, userId, score);

        Long rank = redisTemplate.opsForZSet().rank(key, userId);

        return rank != null ? rank : -1;
    }

    private boolean canEnter(Long concertId, Long scheduleId, String userId) {
        String queueKey = getQueueKey(concertId, scheduleId);
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId);
        if (rank == null) {
            return false;
        }

        String activeStr = redisTemplate.opsForValue().get(getActiveKey(concertId, scheduleId));
        long activeCount = activeStr == null ? 0 : Long.parseLong(activeStr);
        long availableSlots = MAX_SEAT_CAPACITY - activeCount;
        if (availableSlots <= 0) {
            return false;
        }

        if (rank < availableSlots) {
            Long removed = redisTemplate.opsForZSet().remove(queueKey, userId);
            return removed != null && removed > 0;
        }
        return false;
    }

    private String issueEntryToken(Long userId, Long concertId, Long scheduleId) {

        String entryToken = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
                "seat:entry:" + entryToken,
                userId + ":" + concertId + ":" + scheduleId,
                Duration.ofSeconds(180) // 3분 유효
        );

        return entryToken;
    }

    public QueueStatusResponse getStatus(Long concertId, Long scheduleId, Long userId) {
        validateScheduleBelongsToConcert(concertId, scheduleId);
        String queueKey = getQueueKey(concertId, scheduleId);
        Long rank = redisTemplate.opsForZSet()
                .rank(queueKey, String.valueOf(userId));

        if (rank == null) {
            return QueueStatusResponse.waiting(null);
        }

        if (canEnter(concertId, scheduleId, String.valueOf(userId))) {
            String entryToken = issueEntryToken(userId, concertId, scheduleId);
            return QueueStatusResponse.enter(entryToken);
        }

        return QueueStatusResponse.waiting(rank + 1);
    }

    private void validateScheduleBelongsToConcert(Long concertId, Long scheduleId) {
        if (concertId == null) {
            throw new IllegalArgumentException("concertId is required");
        }
        if (scheduleId == null) {
            throw new IllegalArgumentException("scheduleId is required");
        }

        String sql = "SELECT COUNT(1) FROM schedules WHERE id = ? AND concert_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, scheduleId, concertId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException(
                    "회차를 찾을 수 없습니다. concertId=" + concertId + ", scheduleId=" + scheduleId
            );
        }
    }

    private String getQueueKey(Long concertId, Long scheduleId) {
        return "queue:concert:" + concertId + ":schedule:" + scheduleId;
    }

    private String getActiveKey(Long concertId, Long scheduleId) {
        return "seat:active:concert:" + concertId + ":schedule:" + scheduleId;
    }
}
