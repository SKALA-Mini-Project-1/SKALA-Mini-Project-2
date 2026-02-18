package com.example.SKALA_Mini_Project_1.global.redis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
class RedisLockRepositoryTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private RedisLockRepository redisLockRepository;

    @Test
    void unlockSeatIfOwnerDeletesKeyWhenOwnerMatches() {
        Long concertId = 1L;
        Long scheduleId = 2L;
        Long seatId = 3L;
        String userId = "101";
        String key = RedisKeyGenerator.seatLockKey(concertId, scheduleId, seatId);
        String holdSetKey = RedisKeyGenerator.seatUserHoldsKey(concertId, scheduleId, userId);

        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of(key, holdSetKey)),
                eq(userId),
                eq(String.valueOf(seatId))
        ))
                .thenReturn(1L);

        boolean unlocked = redisLockRepository.unlockSeatIfOwner(concertId, scheduleId, seatId, userId);

        assertTrue(unlocked);
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of(key, holdSetKey)),
                eq(userId),
                eq(String.valueOf(seatId))
        );
    }

    @Test
    void unlockSeatIfOwnerReturnsFalseWhenOwnerDoesNotMatch() {
        Long concertId = 1L;
        Long scheduleId = 2L;
        Long seatId = 3L;
        String userId = "101";
        String key = RedisKeyGenerator.seatLockKey(concertId, scheduleId, seatId);
        String holdSetKey = RedisKeyGenerator.seatUserHoldsKey(concertId, scheduleId, userId);

        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                eq(List.of(key, holdSetKey)),
                eq(userId),
                eq(String.valueOf(seatId))
        ))
                .thenReturn(0L);

        boolean unlocked = redisLockRepository.unlockSeatIfOwner(concertId, scheduleId, seatId, userId);

        assertFalse(unlocked);
    }

    @Test
    void decrementSeatActiveFloorZeroReturnsDecrementedValue() {
        Long concertId = 1L;
        Long scheduleId = 2L;
        String activeKey = RedisKeyGenerator.seatActiveKey(concertId, scheduleId);

        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(activeKey))))
                .thenReturn(4L);

        long active = redisLockRepository.decrementSeatActiveFloorZero(concertId, scheduleId);

        assertEquals(4L, active);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), eq(List.of(activeKey)));
    }

    @Test
    void decrementSeatActiveFloorZeroHandlesNullAsZero() {
        Long concertId = 1L;
        Long scheduleId = 2L;
        String activeKey = RedisKeyGenerator.seatActiveKey(concertId, scheduleId);

        when(redisTemplate.execute(any(DefaultRedisScript.class), eq(List.of(activeKey))))
                .thenReturn(null);

        long active = redisLockRepository.decrementSeatActiveFloorZero(concertId, scheduleId);

        assertEquals(0L, active);
    }
}
