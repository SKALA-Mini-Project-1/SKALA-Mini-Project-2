package com.example.SKALA_Mini_Project_1.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.SKALA_Mini_Project_1.global.jwt.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // REST API이므로 CSRF 비활성화
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // JWT 사용으로 세션 미사용
            )
            .authorizeHttpRequests(auth -> auth
                // 결제 관련 API는 인증 없이 접근 허용
                .requestMatchers("/payments/**").permitAll()
                .requestMatchers("/toss/**").permitAll()
                // ✨ Swagger 관련 경로는 모두 허용
                .requestMatchers(
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/swagger-ui.html"
                ).permitAll()
                // 인증 없이 접근 가능한 API
                .requestMatchers(
                    "/api/users/signup",
                    "/api/users/login",
                    "/api/users/logout",
                    "/api/users/email/**"
                ).permitAll()
                // 나머지는 인증 필요
                .anyRequest().authenticated()
            )
            // JWT 필터 추가
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}