package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import java.util.concurrent.locks.LockSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QueueRedisRetryPolicy {

    private final int maxAttempts;
    private final long waitMillis;

    public QueueRedisRetryPolicy(
            @Value("${queue.redis.retry.max-attempts:2}") int maxAttempts,
            @Value("${queue.redis.retry.wait-millis:80}") long waitMillis
    ) {
        this.maxAttempts = maxAttempts;
        this.waitMillis = waitMillis;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public void pauseBeforeRetry() {
        LockSupport.parkNanos(waitMillis * 1_000_000L);
    }
}
