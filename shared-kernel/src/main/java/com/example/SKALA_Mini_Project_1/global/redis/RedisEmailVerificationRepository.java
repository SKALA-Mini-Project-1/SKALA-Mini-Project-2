package com.example.SKALA_Mini_Project_1.global.redis;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisEmailVerificationRepository {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // Redis Key 접두사
    private static final String EMAIL_CODE_PREFIX = "email:verification:code:";
    private static final String EMAIL_RESEND_PREFIX = "email:verification:resend:";
    private static final String EMAIL_VERIFIED_PREFIX = "email:verification:verified:";
    
    /**
     * 인증 코드 저장
     */
    public void saveVerificationCode(String email, String code, long expirationMinutes) {
        String key = EMAIL_CODE_PREFIX + email;
        redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(expirationMinutes));
        log.info("인증 코드 저장 완료 - 이메일: {}, TTL: {}분", email, expirationMinutes);
    }
    
    /**
     * 인증 코드 조회
     */
    public Optional<String> getVerificationCode(String email) {
        String key = EMAIL_CODE_PREFIX + email;
        String code = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(code);
    }
    
    /**
     * 인증 코드 삭제
     */
    public void deleteVerificationCode(String email) {
        String key = EMAIL_CODE_PREFIX + email;
        redisTemplate.delete(key);
        log.info("인증 코드 삭제 완료 - 이메일: {}", email);
    }
    
    /**
     * 재발송 제한 설정
     */
    public void setResendLimit(String email, long resendIntervalSeconds) {
        String key = EMAIL_RESEND_PREFIX + email;
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(resendIntervalSeconds));
        log.info("재발송 제한 설정 - 이메일: {}, 대기 시간: {}초", email, resendIntervalSeconds);
    }
    
    /**
     * 재발송 가능 여부 확인
     */
    public boolean canResend(String email) {
        String key = EMAIL_RESEND_PREFIX + email;
        return !redisTemplate.hasKey(key);
    }
    
    /**
     * 재발송 가능 시간 조회
     */
    public Long getResendAvailableTime(String email) {
        String key = EMAIL_RESEND_PREFIX + email;
        Long ttl = redisTemplate.getExpire(key);
        
        if (ttl == null || ttl <= 0) {
            return null;
        }
        
        return System.currentTimeMillis() / 1000 + ttl;
    }
    
    /**
     * 인증 코드 남은 만료 시간 조회
     */
    public long getCodeTtl(String email) {
        String key = EMAIL_CODE_PREFIX + email;
        Long ttl = redisTemplate.getExpire(key);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }
    
    /**
     * 이메일 인증 완료 플래그 저장
     */
    public void markAsVerified(String email, long expirationMinutes) {
        String key = EMAIL_VERIFIED_PREFIX + email;
        redisTemplate.opsForValue().set(key, "1", Duration.ofMinutes(expirationMinutes));
        log.info("이메일 인증 완료 플래그 저장 - 이메일: {}, TTL: {}분", email, expirationMinutes);
    }
    
    /**
     * 이메일 인증 완료 여부 확인
     */
    public boolean isVerified(String email) {
        String key = EMAIL_VERIFIED_PREFIX + email;
        return redisTemplate.hasKey(key);
    }
    
    /**
     * 이메일 인증 완료 플래그 삭제
     */
    public void deleteVerifiedFlag(String email) {
        String key = EMAIL_VERIFIED_PREFIX + email;
        redisTemplate.delete(key);
        log.info("이메일 인증 완료 플래그 삭제 - 이메일: {}", email);
    }
}