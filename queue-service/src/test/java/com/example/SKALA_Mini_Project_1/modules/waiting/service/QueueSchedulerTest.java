package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class QueueSchedulerTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SetOperations<String, String> setOperations;
    private ZSetOperations<String, String> zSetOperations;
    private QueueService queueService;
    private QueueScheduler queueScheduler;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        setOperations = mock(SetOperations.class);
        zSetOperations = mock(ZSetOperations.class);
        queueService = mock(QueueService.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        queueScheduler = new QueueScheduler(redisTemplate, queueService);
    }

    @Test
    void removesOnlyMembersWithoutHeartbeatAndCleansUpQueueIndex() {
        String queueKey = RedisKeyGenerator.queueKey(1L, 2L);

        when(valueOperations.setIfAbsent(eq("queue:scheduler:cleanup:lock"), any(), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        when(setOperations.members(RedisKeyGenerator.queueIndexKey()))
                .thenReturn(Set.of(queueKey));
        when(zSetOperations.range(queueKey, 0, -1))
                .thenReturn(Set.of("1", "2"));
        when(queueService.hasQueueHeartbeat(1L, 2L, "1")).thenReturn(true);
        when(queueService.hasQueueHeartbeat(1L, 2L, "2")).thenReturn(false);
        when(redisTemplate.execute(anyScript(), anyKeys(), any(), any())).thenReturn(1L);
        when(redisTemplate.execute(anyScript(), anyKeys(), any())).thenReturn(1L);

        queueScheduler.cleanupStaleQueueMembers();

        verify(zSetOperations).remove(queueKey, "2");
        verify(zSetOperations, never()).remove(queueKey, "1");
        verify(queueService).cleanupQueueIndexIfEmpty(queueKey);
    }

    @SuppressWarnings("unchecked")
    private DefaultRedisScript<Long> anyScript() {
        return any(DefaultRedisScript.class);
    }

    @SuppressWarnings("unchecked")
    private List<String> anyKeys() {
        return anyList();
    }
}
