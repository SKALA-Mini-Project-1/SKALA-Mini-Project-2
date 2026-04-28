package com.example.SKALA_Mini_Project_1.global.jwt;

import java.io.IOException;
import java.util.ArrayList;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.SKALA_Mini_Project_1.global.redis.RedisTokenBlacklistService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtUtil jwtUtil;
    private final RedisTokenBlacklistService blacklistService;
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        try {
            // 1. 헤더에서 JWT 토큰 추출
            String token = extractTokenFromRequest(request);
            
            // 2. 토큰 유효성 검증
            if (token != null && jwtUtil.validateToken(token)) {
                // ✨ 블랙리스트 확인 - 로그아웃된 토큰인지 체크
                if (blacklistService.isBlacklisted(token)) {
                    logger.warn("블랙리스트에 등록된 토큰 사용 시도");
                    filterChain.doFilter(request, response);
                    return;
                }
                // 3. 토큰에서 사용자 정보 추출
                Long userId = jwtUtil.getUserIdFromToken(token);
                String email = jwtUtil.getEmailFromToken(token);
                
                // 4. Spring Security 인증 객체 생성
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,  // principal
                                null,    // credentials
                                new ArrayList<>()  // authorities (권한)
                        );
                
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                
                // 5. SecurityContext에 인증 정보 저장
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("JWT 인증 실패", e);
        }
        
        // 6. 다음 필터로 진행
        filterChain.doFilter(request, response);
    }
    
    // 요청 헤더에서 토큰 추출
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // "Bearer " 제거
        }
        
        return null;
    }
}