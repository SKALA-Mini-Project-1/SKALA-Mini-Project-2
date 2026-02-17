package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import com.example.SKALA_Mini_Project_1.modules.users.UserRepository;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.QueueStatusResponse;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.TicketingStartResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
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
    private static final int MAX_TEST_SEED_COUNT = 5000;
    private volatile String concertCodeColumn;
    @Value("${queue.test.enabled:true}")
    private boolean queueTestEnabled;

    public TicketingStartResponse startTicketing(String concertCode, Long scheduleId, Long userId) {

        userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        Long concertId = resolveConcertIdByCode(concertCode);
        validateScheduleBelongsToConcert(concertId, scheduleId);
        long rank = enterQueue(concertId, scheduleId, String.valueOf(userId), 0);
        return TicketingStartResponse.waiting(rank + 1);
    }

    private long enterQueue(Long concertId, Long scheduleId, String userId, long fandomWeightMillis) {

        String key = getQueueKey(concertId, scheduleId);
        redisTemplate.opsForValue().setIfAbsent(getActiveKey(concertId, scheduleId), "0");

        long now = System.currentTimeMillis();
        long weight = Math.min(fandomWeightMillis, MAX_WEIGHT_MILLIS);
        long jitter = ThreadLocalRandom.current().nextLong(0, 50);

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

    public QueueStatusResponse getStatus(String concertCode, Long scheduleId, Long userId) {
        Long concertId = resolveConcertIdByCode(concertCode);
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

    public Long seedQueueAheadForTest(String concertCode, Long scheduleId, Long userId, int count) {
        if (!queueTestEnabled) {
            throw new IllegalStateException("queue test mode is disabled");
        }
        if (count <= 0 || count > MAX_TEST_SEED_COUNT) {
            throw new IllegalArgumentException("count must be between 1 and " + MAX_TEST_SEED_COUNT);
        }

        Long concertId = resolveConcertIdByCode(concertCode);
        validateScheduleBelongsToConcert(concertId, scheduleId);
        String queueKey = getQueueKey(concertId, scheduleId);
        String currentUser = String.valueOf(userId);

        Long currentRank = redisTemplate.opsForZSet().rank(queueKey, currentUser);
        if (currentRank == null) {
            enterQueue(concertId, scheduleId, currentUser, 0);
            currentRank = redisTemplate.opsForZSet().rank(queueKey, currentUser);
        }

        Double currentScore = redisTemplate.opsForZSet().score(queueKey, currentUser);
        if (currentScore == null) {
            throw new IllegalStateException("failed to resolve current user queue score");
        }

        for (int i = 0; i < count; i++) {
            String fakeUserId = "test-user-" + userId + "-" + UUID.randomUUID();
            double fakeScore = currentScore - (count - i) - ThreadLocalRandom.current().nextDouble(0.001, 0.999);
            redisTemplate.opsForZSet().add(queueKey, fakeUserId, fakeScore);
        }

        Long rankAfterSeed = redisTemplate.opsForZSet().rank(queueKey, currentUser);
        return rankAfterSeed == null ? null : rankAfterSeed + 1;
    }

    private Long resolveConcertIdByCode(String concertCode) {
        if (concertCode == null || concertCode.isBlank()) {
            throw new IllegalArgumentException("concertCode is required");
        }

        String column = getConcertCodeColumn();
        List<Long> ids;
        if (column != null) {
            String sql = "SELECT id FROM concerts WHERE " + column + " = ? LIMIT 1";
            ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), concertCode);
        } else {
            Long concertId;
            try {
                concertId = Long.parseLong(concertCode);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("concertCode 컬럼이 없어서 숫자형 concertId만 사용할 수 있습니다. concertCode=" + concertCode);
            }

            String sql = "SELECT id FROM concerts WHERE id = ? LIMIT 1";
            ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), concertId);
        }

        if (ids.isEmpty()) {
            throw new IllegalArgumentException("콘서트를 찾을 수 없습니다. concertCode=" + concertCode);
        }
        return ids.get(0);
    }

    private void validateScheduleBelongsToConcert(Long concertId, Long scheduleId) {
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

    private String getConcertCodeColumn() {
        if (concertCodeColumn != null) {
            return "__NONE__".equals(concertCodeColumn) ? null : concertCodeColumn;
        }

        String sql = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_name = 'concerts'
                  AND column_name IN ('concert_code', 'code')
                ORDER BY CASE WHEN column_name = 'concert_code' THEN 0 ELSE 1 END
                LIMIT 1
                """;
        List<String> columns = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("column_name"));
        if (columns.isEmpty()) {
            concertCodeColumn = "__NONE__";
            return null;
        }
        concertCodeColumn = columns.get(0);
        return concertCodeColumn;
    }

    private String getQueueKey(Long concertId, Long scheduleId) {
        return "queue:concert:" + concertId + ":schedule:" + scheduleId;
    }

    private String getActiveKey(Long concertId, Long scheduleId) {
        return "seat:active:concert:" + concertId + ":schedule:" + scheduleId;
    }
}
