package com.example.SKALA_Mini_Project_1.global.redis;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenBlacklistService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final String BLACKLIST_PREFIX = "blacklist:token:";
    
    /**
     * 토큰을 블랙리스트에 추가
     * @param token JWT 토큰
     * @param expirationTimeMs 토큰 만료 시간 (밀리초)
     */
    public void addToBlacklist(String token, long expirationTimeMs) {
        String key = BLACKLIST_PREFIX + token;
        
        // Redis에 토큰 저장 (value는 "blacklisted", TTL은 토큰 만료시간)
        redisTemplate.opsForValue().set(
            key, 
            "blacklisted", 
            expirationTimeMs, 
            TimeUnit.MILLISECONDS
        );
        
        log.info("토큰이 블랙리스트에 추가됨: {}", token.substring(0, Math.min(20, token.length())) + "...");
    }
    
    /**
     * 토큰이 블랙리스트에 있는지 확인
     * @param token JWT 토큰
     * @return 블랙리스트에 있으면 true
     */
    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        Boolean exists = redisTemplate.hasKey(key);
        return exists != null && exists;
    }
    
    /**
     * 블랙리스트에서 토큰 제거 (테스트용, 실제로는 TTL로 자동 삭제됨)
     * @param token JWT 토큰
     */
    public void removeFromBlacklist(String token) {
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.delete(key);
        log.info("토큰이 블랙리스트에서 제거됨: {}", token.substring(0, Math.min(20, token.length())) + "...");
    }
}