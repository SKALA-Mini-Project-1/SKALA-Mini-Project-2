package com.example.SKALA_Mini_Project_1.global.redis;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisEmailVerificationRepository {
    
    private final RedisTemplate<String, String> redisTemplate;

    private final Map<String, ExpiringValue> verificationCodeFallback = new ConcurrentHashMap<>();
    private final Map<String, ExpiringValue> resendLimitFallback = new ConcurrentHashMap<>();
    private final Map<String, ExpiringValue> verifiedFlagFallback = new ConcurrentHashMap<>();
    
    // Redis Key 접두사
    private static final String EMAIL_CODE_PREFIX = "email:verification:code:";
    private static final String EMAIL_RESEND_PREFIX = "email:verification:resend:";
    private static final String EMAIL_VERIFIED_PREFIX = "email:verification:verified:";
    
    /**
     * 인증 코드 저장
     */
    public void saveVerificationCode(String email, String code, long expirationMinutes) {
        String key = EMAIL_CODE_PREFIX + email;
        Duration ttl = Duration.ofMinutes(expirationMinutes);

        try {
            redisTemplate.opsForValue().set(key, code, ttl);
            verificationCodeFallback.remove(key);
            log.info("인증 코드 저장 완료 - 이메일: {}, TTL: {}분", email, expirationMinutes);
        } catch (Exception e) {
            saveFallbackValue(verificationCodeFallback, key, code, ttl, "인증 코드", email, e);
        }
    }
    
    /**
     * 인증 코드 조회
     */
    public Optional<String> getVerificationCode(String email) {
        String key = EMAIL_CODE_PREFIX + email;

        try {
            String code = redisTemplate.opsForValue().get(key);
            if (code != null) {
                return Optional.of(code);
            }
        } catch (Exception e) {
            log.warn("Redis 인증 코드 조회 실패 - 이메일: {}. 메모리 폴백을 확인합니다.", email, e);
        }

        return getFallbackValue(verificationCodeFallback, key);
    }
    
    /**
     * 인증 코드 삭제
     */
    public void deleteVerificationCode(String email) {
        String key = EMAIL_CODE_PREFIX + email;
        verificationCodeFallback.remove(key);

        try {
            redisTemplate.delete(key);
            log.info("인증 코드 삭제 완료 - 이메일: {}", email);
        } catch (Exception e) {
            log.warn("Redis 인증 코드 삭제 실패 - 이메일: {}. 메모리 폴백만 정리했습니다.", email, e);
        }
    }
    
    /**
     * 재발송 제한 설정
     */
    public void setResendLimit(String email, long resendIntervalSeconds) {
        String key = EMAIL_RESEND_PREFIX + email;
        Duration ttl = Duration.ofSeconds(resendIntervalSeconds);

        try {
            redisTemplate.opsForValue().set(key, "1", ttl);
            resendLimitFallback.remove(key);
            log.info("재발송 제한 설정 - 이메일: {}, 대기 시간: {}초", email, resendIntervalSeconds);
        } catch (Exception e) {
            saveFallbackValue(resendLimitFallback, key, "1", ttl, "재발송 제한", email, e);
        }
    }
    
    /**
     * 재발송 가능 여부 확인
     */
    public boolean canResend(String email) {
        String key = EMAIL_RESEND_PREFIX + email;
        if (getFallbackValue(resendLimitFallback, key).isPresent()) {
            return false;
        }

        try {
            return !Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Redis 재발송 제한 조회 실패 - 이메일: {}. 메모리 폴백 기준으로 처리합니다.", email, e);
            return true;
        }
    }
    
    /**
     * 재발송 가능 시간 조회
     */
    public Long getResendAvailableTime(String email) {
        String key = EMAIL_RESEND_PREFIX + email;

        try {
            Long ttl = redisTemplate.getExpire(key);
            if (ttl != null && ttl > 0) {
                return System.currentTimeMillis() / 1000 + ttl;
            }
        } catch (Exception e) {
            log.warn("Redis 재발송 제한 TTL 조회 실패 - 이메일: {}. 메모리 폴백을 확인합니다.", email, e);
        }

        return getFallbackExpireAtEpochSeconds(resendLimitFallback, key);
    }
    
    /**
     * 인증 코드 남은 만료 시간 조회
     */
    public long getCodeTtl(String email) {
        String key = EMAIL_CODE_PREFIX + email;
        try {
            Long ttl = redisTemplate.getExpire(key);
            if (ttl != null && ttl > 0) {
                return ttl;
            }
        } catch (Exception e) {
            log.warn("Redis 인증 코드 TTL 조회 실패 - 이메일: {}. 메모리 폴백을 확인합니다.", email, e);
        }

        return getFallbackTtlSeconds(verificationCodeFallback, key);
    }
    
    /**
     * 이메일 인증 완료 플래그 저장
     */
    public void markAsVerified(String email, long expirationMinutes) {
        String key = EMAIL_VERIFIED_PREFIX + email;
        Duration ttl = Duration.ofMinutes(expirationMinutes);

        try {
            redisTemplate.opsForValue().set(key, "1", ttl);
            verifiedFlagFallback.remove(key);
            log.info("이메일 인증 완료 플래그 저장 - 이메일: {}, TTL: {}분", email, expirationMinutes);
        } catch (Exception e) {
            saveFallbackValue(verifiedFlagFallback, key, "1", ttl, "이메일 인증 완료 플래그", email, e);
        }
    }
    
    /**
     * 이메일 인증 완료 여부 확인
     */
    public boolean isVerified(String email) {
        String key = EMAIL_VERIFIED_PREFIX + email;
        if (getFallbackValue(verifiedFlagFallback, key).isPresent()) {
            return true;
        }

        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Redis 이메일 인증 완료 여부 조회 실패 - 이메일: {}. 메모리 폴백 기준으로 처리합니다.", email, e);
            return false;
        }
    }
    
    /**
     * 이메일 인증 완료 플래그 삭제
     */
    public void deleteVerifiedFlag(String email) {
        String key = EMAIL_VERIFIED_PREFIX + email;
        verifiedFlagFallback.remove(key);

        try {
            redisTemplate.delete(key);
            log.info("이메일 인증 완료 플래그 삭제 - 이메일: {}", email);
        } catch (Exception e) {
            log.warn("Redis 이메일 인증 완료 플래그 삭제 실패 - 이메일: {}. 메모리 폴백만 정리했습니다.", email, e);
        }
    }

    private void saveFallbackValue(
            Map<String, ExpiringValue> storage,
            String key,
            String value,
            Duration ttl,
            String label,
            String email,
            Exception e
    ) {
        storage.put(key, new ExpiringValue(value, Instant.now().plus(ttl)));
        log.warn("Redis {} 저장 실패 - 이메일: {}. 메모리 폴백으로 전환합니다.", label, email, e);
    }

    private Optional<String> getFallbackValue(Map<String, ExpiringValue> storage, String key) {
        ExpiringValue value = storage.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value.isExpired()) {
            storage.remove(key);
            return Optional.empty();
        }
        return Optional.of(value.value());
    }

    private Long getFallbackExpireAtEpochSeconds(Map<String, ExpiringValue> storage, String key) {
        ExpiringValue value = storage.get(key);
        if (value == null) {
            return null;
        }
        if (value.isExpired()) {
            storage.remove(key);
            return null;
        }
        return value.expiresAt().getEpochSecond();
    }

    private long getFallbackTtlSeconds(Map<String, ExpiringValue> storage, String key) {
        ExpiringValue value = storage.get(key);
        if (value == null) {
            return 0;
        }
        if (value.isExpired()) {
            storage.remove(key);
            return 0;
        }
        return Math.max(0, Duration.between(Instant.now(), value.expiresAt()).getSeconds());
    }

    private record ExpiringValue(String value, Instant expiresAt) {
        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
