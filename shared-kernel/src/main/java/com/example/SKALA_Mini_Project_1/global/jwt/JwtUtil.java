package com.example.SKALA_Mini_Project_1.global.jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;


@Component
public class JwtUtil {
    
    private final Key secretKey;
    private final long expirationTime;
    
    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationTime
    ) {
        // 0.11.5 버전에서는 Key 타입 사용
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationTime = expirationTime;
    }
    
    // 토큰 생성
    public String generateToken(Long userId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);
        
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("email", email)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
    // 토큰에서 사용자 ID 추출
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }
    
    // 토큰에서 이메일 추출
    public String getEmailFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("email", String.class);
    }
    
    // 토큰 만료 시간까지 남은 시간 (밀리초) 계산
    public long getExpirationTimeMs(String token) {
        Claims claims = parseClaims(token);
        Date expiration = claims.getExpiration();
        Date now = new Date();
        
        long remainingTime = expiration.getTime() - now.getTime();
        return Math.max(remainingTime, 0); // 음수 방지
    }

    // 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    // 토큰 파싱
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 대기열 입장 토큰 생성
    public String generateQueuePassToken(Long userId, String email, Long concertId) {

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 60000); // 1분 유효 (우리가 정함)

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("email", email)
                .claim("concertId", concertId)
                .claim("type", "QUEUE_PASS")
                .claim("oneTime", true)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

}