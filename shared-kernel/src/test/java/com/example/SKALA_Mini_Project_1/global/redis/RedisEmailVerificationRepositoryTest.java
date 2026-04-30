package com.example.SKALA_Mini_Project_1.global.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisEmailVerificationRepositoryTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisEmailVerificationRepository repository;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        repository = new RedisEmailVerificationRepository(redisTemplate);
    }

    @Test
    void usesFallbackWhenSavingVerificationCodeFails() {
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));

        repository.saveVerificationCode("user@example.com", "123456", 5);

        assertThat(repository.getVerificationCode("user@example.com"))
                .contains("123456");
        assertThat(repository.getCodeTtl("user@example.com")).isPositive();
    }

    @Test
    void usesFallbackWhenSavingVerifiedFlagFails() {
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));

        repository.markAsVerified("user@example.com", 30);

        assertThat(repository.isVerified("user@example.com")).isTrue();

        repository.deleteVerifiedFlag("user@example.com");

        assertThat(repository.isVerified("user@example.com")).isFalse();
    }

    @Test
    void usesFallbackForResendLimitWhenRedisIsUnavailable() {
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(redisTemplate.getExpire(anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        repository.setResendLimit("user@example.com", 60);

        assertThat(repository.canResend("user@example.com")).isFalse();
        assertThat(repository.getResendAvailableTime("user@example.com")).isNotNull();
    }
}
