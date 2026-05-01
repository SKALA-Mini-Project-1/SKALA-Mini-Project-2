package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class ActiveSeatCleanupSchedulerTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SetOperations<String, String> setOperations;
    private ActiveSeatCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        setOperations = mock(SetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        scheduler = new ActiveSeatCleanupScheduler(redisTemplate);
    }

    @Test
    void removesMissingActiveKeyFromIndex() {
        String activeKey = RedisKeyGenerator.seatActiveKey(1L, 2L);
        Cursor<String> activeCursor = cursorWith(activeKey);

        when(valueOperations.setIfAbsent(eq("active:scheduler:sync:lock"), any(), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        when(setOperations.scan(eq(RedisKeyGenerator.seatActiveIndexKey()), any(ScanOptions.class)))
                .thenReturn(activeCursor);
        when(redisTemplate.hasKey(activeKey)).thenReturn(false);
        when(redisTemplate.execute(anyScript(), anyKeys(), any(), any())).thenReturn(1L);
        when(redisTemplate.execute(anyScript(), anyKeys(), any())).thenReturn(1L);

        scheduler.syncActiveCount();

        verify(setOperations).remove(RedisKeyGenerator.seatActiveIndexKey(), activeKey);
    }

    @Test
    void recalculatesActiveCountAndRemovesStaleAccessMembers() {
        Long concertId = 1L;
        Long scheduleId = 2L;
        String activeKey = RedisKeyGenerator.seatActiveKey(concertId, scheduleId);
        String accessIndexKey = RedisKeyGenerator.seatAccessIndexKey(concertId, scheduleId);
        String aliveAccessKey = RedisKeyGenerator.seatAccessKey(1L, concertId, scheduleId);
        String staleAccessKey = RedisKeyGenerator.seatAccessKey(2L, concertId, scheduleId);
        String staleAccessByScheduleKey = RedisKeyGenerator.seatAccessByScheduleKey(2L, scheduleId);

        Cursor<String> activeCursor = cursorWith(activeKey);
        Cursor<String> memberCursor = cursorWith("1", "2", "oops");

        when(valueOperations.setIfAbsent(eq("active:scheduler:sync:lock"), any(), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        when(setOperations.scan(eq(RedisKeyGenerator.seatActiveIndexKey()), any(ScanOptions.class)))
                .thenReturn(activeCursor);
        when(setOperations.scan(eq(accessIndexKey), any(ScanOptions.class)))
                .thenReturn(memberCursor);
        when(redisTemplate.hasKey(activeKey)).thenReturn(true);
        when(redisTemplate.hasKey(aliveAccessKey)).thenReturn(true);
        when(redisTemplate.hasKey(staleAccessKey)).thenReturn(false);
        when(redisTemplate.hasKey(staleAccessByScheduleKey)).thenReturn(false);
        when(redisTemplate.execute(anyScript(), anyKeys(), any(), any())).thenReturn(1L);
        when(redisTemplate.execute(anyScript(), anyKeys(), any())).thenReturn(1L);

        scheduler.syncActiveCount();

        ArgumentCaptor<Object[]> staleUsersCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(setOperations).remove(eq(accessIndexKey), staleUsersCaptor.capture());
        verify(valueOperations).set(activeKey, "1");
        Object[] staleUsers = staleUsersCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(staleUsers)
                .contains("2", "oops");
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> cursorWith(String... values) {
        Cursor<String> cursor = mock(Cursor.class);
        Boolean[] hasNextAnswers = new Boolean[values.length + 1];
        for (int i = 0; i < values.length; i++) {
            hasNextAnswers[i] = true;
        }
        hasNextAnswers[values.length] = false;
        when(cursor.hasNext()).thenReturn(hasNextAnswers[0], java.util.Arrays.copyOfRange(hasNextAnswers, 1, hasNextAnswers.length));
        when(cursor.next()).thenReturn(values[0], java.util.Arrays.copyOfRange(values, 1, values.length));
        return cursor;
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
