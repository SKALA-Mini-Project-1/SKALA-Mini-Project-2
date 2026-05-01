package com.example.SKALA_Mini_Project_1.global.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

class RedisLockRepositoryTest {

    private RedisTemplate<String, String> redisTemplate;
    private SetOperations<String, String> setOperations;
    private ValueOperations<String, String> valueOperations;
    private RedisLockRepository repository;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        setOperations = mock(SetOperations.class);
        valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        repository = new RedisLockRepository(redisTemplate);
    }

    @Test
    void countUserHeldSeatsPrunesStaleEntriesBeforeCounting() {
        String holdSetKey = "seat:user:holds:concert:1001:schedule:1001:user:4";
        when(setOperations.members(holdSetKey)).thenReturn(Set.of("1357", "1507"));
        when(valueOperations.get("seat:concert:1001:schedule:1001:seatId:1357")).thenReturn("4");
        when(valueOperations.get("seat:concert:1001:schedule:1001:seatId:1507")).thenReturn(null);
        when(setOperations.size(holdSetKey)).thenReturn(1L);

        int count = repository.countUserHeldSeats(1001L, 1001L, "4");

        assertThat(count).isEqualTo(1);
        verify(setOperations).remove(holdSetKey, "1507");
    }

    @Test
    void releaseUserHeldSeatsRemovesStaleEntriesEvenWhenSeatLocksExpired() {
        String holdSetKey = "seat:user:holds:concert:1001:schedule:1001:user:4";
        when(setOperations.members(holdSetKey)).thenReturn(Set.of("1357", "1507"));
        when(valueOperations.get("seat:concert:1001:schedule:1001:seatId:1357")).thenReturn(null);
        when(valueOperations.get("seat:concert:1001:schedule:1001:seatId:1507")).thenReturn(null);
        when(redisTemplate.hasKey(holdSetKey)).thenReturn(false);

        int released = repository.releaseUserHeldSeats(1001L, 1001L, "4");

        assertThat(released).isZero();
        verify(setOperations).remove(holdSetKey, "1357");
        verify(setOperations).remove(holdSetKey, "1507");
        verify(redisTemplate).delete(holdSetKey);
    }
}
