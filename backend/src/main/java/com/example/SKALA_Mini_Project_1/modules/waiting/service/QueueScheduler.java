package com.example.SKALA_Mini_Project_1.modules.waiting.service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;


@Component
@RequiredArgsConstructor
public class QueueScheduler {
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(fixedDelay = 3000)
public void openEntrance() {

    Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent("queue:scheduler:lock", "1", Duration.ofSeconds(2));

    if (!Boolean.TRUE.equals(locked)) {
        return; // 다른 서버가 실행 중이면 그냥 종료
    }

    String enterKey = "queue:concert:1:enter";

    String current = redisTemplate.opsForValue().get(enterKey);

    long allowed = current == null ? 0 : Long.parseLong(current);

    allowed += 100;

    redisTemplate.opsForValue().set(enterKey, String.valueOf(allowed));
}
}
