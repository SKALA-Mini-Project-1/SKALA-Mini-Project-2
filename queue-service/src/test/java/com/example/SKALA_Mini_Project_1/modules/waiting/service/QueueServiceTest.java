package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import com.example.SKALA_Mini_Project_1.integration.concert.ConcertServiceClient;
import com.example.SKALA_Mini_Project_1.integration.userauth.UserAuthClient;
import com.example.SKALA_Mini_Project_1.modules.waiting.config.QueueRuntimeProperties;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.QueueStatusResponse;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.TicketingStartResponse;
import com.example.SKALA_Mini_Project_1.modules.waiting.observability.QueueMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class QueueServiceTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SetOperations<String, String> setOperations;
    private ZSetOperations<String, String> zSetOperations;
    private UserAuthClient userAuthClient;
    private ConcertServiceClient concertServiceClient;
    private QueuePriorityService queuePriorityService;
    private QueueRedisRetryPolicy queueRedisRetryPolicy;
    private QueueRuntimeProperties queueRuntimeProperties;
    private QueueMetrics queueMetrics;
    private QueueService queueService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        setOperations = mock(SetOperations.class);
        zSetOperations = mock(ZSetOperations.class);
        userAuthClient = mock(UserAuthClient.class);
        concertServiceClient = mock(ConcertServiceClient.class);
        queuePriorityService = mock(QueuePriorityService.class);
        queueRedisRetryPolicy = mock(QueueRedisRetryPolicy.class);

        queueRuntimeProperties = new QueueRuntimeProperties();
        queueRuntimeProperties.setMaxSeatCapacity(123);
        queueMetrics = new QueueMetrics(new SimpleMeterRegistry(), ObservationRegistry.create());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(queueRedisRetryPolicy.maxAttempts()).thenReturn(1);

        queueService = new QueueService(
                redisTemplate,
                userAuthClient,
                concertServiceClient,
                queuePriorityService,
                queueRedisRetryPolicy,
                queueRuntimeProperties,
                queueMetrics
        );
    }

    @Test
    void returnsWaitingWithoutRankWhenRedisFailsDuringStatusLookup() {
        Long concertId = 1L;
        Long scheduleId = 2L;
        Long userId = 3L;

        when(redisTemplate.hasKey("queue:schedule:concert:1:schedule:2")).thenReturn(true);
        when(zSetOperations.rank(RedisKeyGenerator.queueKey(concertId, scheduleId), String.valueOf(userId)))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        QueueStatusResponse response = queueService.getStatus(concertId, scheduleId, userId);

        assertThat(response.isEnter()).isFalse();
        assertThat(response.getEntryToken()).isNull();
        assertThat(response.getRank()).isNull();
        verify(redisTemplate, never()).execute(anyScript(), anyKeyList());
    }

    @Test
    void returnsNullWhenConsumingEntryTokenFailsBecauseRedisIsDown() {
        when(redisTemplate.execute(anyScript(), anyKeyList()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        String consumed = queueService.consumeEntryToken("entry-token");

        assertThat(consumed).isNull();
    }

    @Test
    void usesConfiguredMaxSeatCapacityWhenTryingAdmission() {
        Long concertId = 1L;
        Long scheduleId = 2L;
        Long userId = 3L;

        when(redisTemplate.hasKey("queue:schedule:concert:1:schedule:2")).thenReturn(true);
        when(zSetOperations.rank(RedisKeyGenerator.queueKey(concertId, scheduleId), String.valueOf(userId)))
                .thenReturn(0L);
        when(redisTemplate.execute(
                anyScript(),
                anyKeyList(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(null);

        QueueStatusResponse response = queueService.getStatus(concertId, scheduleId, userId);

        assertThat(response.isEnter()).isFalse();
        assertThat(response.getRank()).isEqualTo(1L);
        verify(redisTemplate).execute(
                anyScript(),
                anyKeyList(),
                eq("3"),
                eq("123"),
                eq("3:1:2"),
                eq("180000"),
                anyString()
        );
    }

    @Test
    void startTicketingAddsUserToQueueAndReturnsWaitingRank() {
        Long concertId = 1L;
        Long scheduleId = 2L;
        Long userId = 3L;
        String activeKey = RedisKeyGenerator.seatActiveKey(concertId, scheduleId);
        String queueKey = RedisKeyGenerator.queueKey(concertId, scheduleId);
        String heartbeatKey = RedisKeyGenerator.queueHeartbeatKey(concertId, scheduleId, String.valueOf(userId));

        when(redisTemplate.hasKey("queue:schedule:concert:1:schedule:2")).thenReturn(true);
        when(queuePriorityService.getQueuePriorityBoostMillis(userId, concertId)).thenReturn(250);
        when(valueOperations.setIfAbsent(activeKey, "0")).thenReturn(true);
        when(zSetOperations.add(eq(queueKey), eq("3"), any(Double.class))).thenReturn(true);
        when(zSetOperations.rank(queueKey, "3")).thenReturn(4L);

        TicketingStartResponse response = queueService.startTicketing(concertId, scheduleId, userId);

        assertThat(response.isEnter()).isFalse();
        assertThat(response.getRank()).isEqualTo(5L);
        verify(userAuthClient).ensureUserExists(userId);
        verify(valueOperations).set(eq(heartbeatKey), eq("1"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void getStatusReturnsEntryTokenAndCleansUpQueueStateWhenAdmissionSucceeds() {
        Long concertId = 1L;
        Long scheduleId = 2L;
        Long userId = 3L;
        String queueKey = RedisKeyGenerator.queueKey(concertId, scheduleId);
        String heartbeatKey = RedisKeyGenerator.queueHeartbeatKey(concertId, scheduleId, String.valueOf(userId));

        when(redisTemplate.hasKey("queue:schedule:concert:1:schedule:2")).thenReturn(true);
        when(zSetOperations.rank(queueKey, "3")).thenReturn(0L);
        when(redisTemplate.execute(
                anyScript(),
                anyKeyList(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn("issued-token");
        when(zSetOperations.zCard(queueKey)).thenReturn(0L);

        QueueStatusResponse response = queueService.getStatus(concertId, scheduleId, userId);

        assertThat(response.isEnter()).isTrue();
        assertThat(response.getEntryToken()).isEqualTo("issued-token");
        assertThat(response.getRank()).isNull();
        verify(setOperations).remove(RedisKeyGenerator.queueIndexKey(), queueKey);
        verify(redisTemplate).delete(heartbeatKey);
    }

    @Test
    void leaveQueueRemovesUserAndClearsHeartbeat() {
        Long concertId = 1L;
        Long scheduleId = 2L;
        Long userId = 3L;
        String queueKey = RedisKeyGenerator.queueKey(concertId, scheduleId);
        String heartbeatKey = RedisKeyGenerator.queueHeartbeatKey(concertId, scheduleId, String.valueOf(userId));

        when(redisTemplate.hasKey("queue:schedule:concert:1:schedule:2")).thenReturn(true);
        when(zSetOperations.remove(queueKey, "3")).thenReturn(1L);
        when(zSetOperations.zCard(queueKey)).thenReturn(0L);

        boolean removed = queueService.leaveQueue(concertId, scheduleId, userId);

        assertThat(removed).isTrue();
        verify(setOperations).remove(RedisKeyGenerator.queueIndexKey(), queueKey);
        verify(redisTemplate).delete(heartbeatKey);
    }

    @Test
    void fallsBackToDownstreamValidationWhenScheduleCacheReadWriteFails() {
        Long concertId = 1L;
        Long scheduleId = 2L;
        Long userId = 3L;
        String activeKey = RedisKeyGenerator.seatActiveKey(concertId, scheduleId);
        String queueKey = RedisKeyGenerator.queueKey(concertId, scheduleId);

        when(redisTemplate.hasKey("queue:schedule:concert:1:schedule:2"))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations)
                .set(eq("queue:schedule:concert:1:schedule:2"), eq("1"), eq(Duration.ofMinutes(10)));
        when(queuePriorityService.getQueuePriorityBoostMillis(userId, concertId)).thenReturn(0);
        when(valueOperations.setIfAbsent(activeKey, "0")).thenReturn(true);
        when(zSetOperations.add(eq(queueKey), eq("3"), any(Double.class))).thenReturn(true);
        when(zSetOperations.rank(queueKey, "3")).thenReturn(0L);

        TicketingStartResponse response = queueService.startTicketing(concertId, scheduleId, userId);

        assertThat(response.isEnter()).isFalse();
        assertThat(response.getRank()).isEqualTo(1L);
        verify(concertServiceClient).ensureScheduleBelongsToConcert(concertId, scheduleId);
    }

    @SuppressWarnings("unchecked")
    private DefaultRedisScript<String> anyScript() {
        return any(DefaultRedisScript.class);
    }

    @SuppressWarnings("unchecked")
    private List<String> anyKeyList() {
        return anyList();
    }
}
