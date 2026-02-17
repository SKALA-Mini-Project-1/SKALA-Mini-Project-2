package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import com.example.SKALA_Mini_Project_1.modules.users.User;
import com.example.SKALA_Mini_Project_1.modules.users.UserRepository;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.QueueStatusResponse;
// import com.example.SKALA_Mini_Project_1.modules.seats.domain.Concert;
// import com.example.SKALA_Mini_Project_1.modules.seats.repository.ConcertRepository;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.TicketingStartResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;

    // 🔥 나중에 팬점수 계산용
    // private final ConcertRepository concertRepository;

    private static final long MAX_SEAT_CAPACITY = 500; // 좌석 서버 최대 수용 인원
    private static final int MAX_WEIGHT_MILLIS = 5000; // 팬점수 최대 보정치

    /**
     * 🎯 예매하기 버튼 클릭 시 호출되는 메인 메서드
     */
    public TicketingStartResponse startTicketing(Long concertId, Long scheduleId, Long userId) {

        // 1️ 로그인 사용자 존재 확인
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        long rank = enterQueue(concertId, scheduleId, String.valueOf(userId), 0);


        // // 2️⃣ (현재는 팬점수 미적용)
        // long weightMillis = 0;

        /*
        // 🔥 팬점수 로직 (나중에 활성화)
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new IllegalArgumentException("콘서트 없음"));

        weightMillis = calculateFanWeight(user, concert);
        */

        // *** 일단 주석 처리
        // // 3️⃣ Redis 대기열 진입
        // long rank = enterQueue(concertId, String.valueOf(userId), weightMillis);

        // // 4️⃣ 좌석 서버 수용 가능 여부 확인
        // if (canEnter(concertId, String.valueOf(userId))) {

        //     String entryToken = issueEntryToken(userId);

        //     return TicketingStartResponse.enter(entryToken);
        // }

        return TicketingStartResponse.waiting(rank);
    }

    /**
     * 🔥 Redis ZSET 대기열 진입
     */
    private long enterQueue(Long concertId, Long scheduleId, String userId, long fandomWeightMillis) {

        String key = getQueueKey(concertId, scheduleId);
        String enterKey = getEnterKey(concertId, scheduleId);
        redisTemplate.opsForValue().setIfAbsent(enterKey, "0");

        long now = System.currentTimeMillis();
        long weight = Math.min(fandomWeightMillis, MAX_WEIGHT_MILLIS);
        long jitter = ThreadLocalRandom.current().nextLong(0, 50);

        long score = now - weight + jitter;

        redisTemplate.opsForZSet().add(key, userId, score);

        Long rank = redisTemplate.opsForZSet().rank(key, userId);

        System.out.println("ENTER QUEUE CALLED");
        System.out.println("QUEUE KEY: " + getQueueKey(concertId, scheduleId));
        System.out.println("USER ID: " + userId);

        return rank != null ? rank : -1;
    }

    /**
     * 🔥 좌석 서버 수용 인원 기반 입장 판단
     */
    // => 현재는 대기열에서 제거하는 방식으로 입장 처리 (실제 좌석 서버와 연동 시 수정 필요)
    // private boolean canEnter(Long concertId, String userId) {

    //     String queueKey = getQueueKey(concertId);
    //     String activeKey = "seat:active:concert:" + concertId;

    //     Long rank = redisTemplate.opsForZSet().rank(queueKey, userId);
    //     if (rank == null) return false;

    //     String activeStr = redisTemplate.opsForValue().get(activeKey);
    //     long active = activeStr == null ? 0 : Long.parseLong(activeStr);

    //     long available = MAX_SEAT_CAPACITY - active;

    //     if (available <= 0) return false;

    //     if (rank < available) {
    //         redisTemplate.opsForZSet().remove(queueKey, userId);
    //         redisTemplate.opsForValue().increment(activeKey);
    //         return true;
    //     }

    //     return false;
    // }



    // ❗️ 결제 서버와 연결 전까지는 우선 스케쥴러를 사용하는 방식으로 좌석 서버 입장 허용(8081)
    private boolean canEnter(Long concertId, Long scheduleId, String userId) {

        String queueKey = getQueueKey(concertId, scheduleId);
        String enterKey = getEnterKey(concertId, scheduleId);

        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId);
        if (rank == null) return false;

        String allowedStr = redisTemplate.opsForValue().get(enterKey);
        long allowed = allowedStr == null ? 0 : Long.parseLong(allowedStr);

        if (rank < allowed) {
            redisTemplate.opsForZSet().remove(queueKey, userId);
            return true;
        }

        return false;
    }



    /**
     * 🔥 1회용 좌석 진입 토큰 발급
     */
    private String issueEntryToken(Long userId) {

        String entryToken = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
                "seat:entry:" + entryToken,
                String.valueOf(userId),
                Duration.ofSeconds(180) // 3분 유효
        );

        return entryToken;
    }

    /**
     * 🔥 팬점수 계산 (현재 주석)
     */
    /*
    private long calculateFanWeight(User user, Concert concert) {

        if (user.getArtistId() == null) return 0;

        if (!user.getArtistId().equals(concert.getArtistId()))
            return 0;

        int fanScore = user.getFanScore() == null ? 0 : user.getFanScore();

        return Math.min(fanScore * 100L, MAX_WEIGHT_MILLIS);
    }
    */

    public QueueStatusResponse getStatus(Long concertId, Long scheduleId, Long userId) {

        String queueKey = getQueueKey(concertId, scheduleId);
        String enterKey = getEnterKey(concertId, scheduleId);

        Long rank = redisTemplate.opsForZSet()
                .rank(queueKey, String.valueOf(userId));

        System.out.println("getStatus QUEUE KEY: " + queueKey);
        System.out.println("getStatus USER ID: " + userId);
        System.out.println("getStatus RANK: " + rank);

        if (rank == null) {
            // 이미 대기열에서 제거된 상태 (입장했을 가능성)
            return QueueStatusResponse.waiting(null);
        }

        String allowedStr = redisTemplate.opsForValue().get(enterKey);
        long allowed = allowedStr == null ? 0 : Long.parseLong(allowedStr);

        if (rank < allowed) {

            redisTemplate.opsForZSet().remove(queueKey, String.valueOf(userId));

            String entryToken = issueEntryToken(userId);

            return QueueStatusResponse.enter(entryToken);
        }


        return QueueStatusResponse.waiting(rank + 1);
    }



    private String getQueueKey(Long concertId, Long scheduleId) {
        return "queue:concert:" + concertId + ":schedule:" + scheduleId;
    }

    private String getEnterKey(Long concertId, Long scheduleId) {
        return "queue:concert:" + concertId + ":schedule:" + scheduleId + ":enter";
    }
}
