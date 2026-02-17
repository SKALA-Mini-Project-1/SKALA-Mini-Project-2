package com.example.SKALA_Mini_Project_1.modules.users;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.SKALA_Mini_Project_1.global.jwt.JwtUtil;
import com.example.SKALA_Mini_Project_1.global.redis.RedisTokenBlacklistService;
import com.example.SKALA_Mini_Project_1.modules.users.dto.LoginRequest;
import com.example.SKALA_Mini_Project_1.modules.users.dto.LoginResponse;
import com.example.SKALA_Mini_Project_1.modules.users.dto.SignUpRequest;
import com.example.SKALA_Mini_Project_1.modules.users.dto.SignUpResponse;
import com.example.SKALA_Mini_Project_1.modules.users.service.EmailVerificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTokenBlacklistService blacklistService;
    private final EmailVerificationService emailVerificationService;

    
    // 로그인
    public LoginResponse login(LoginRequest request) {
        // 1. 이메일로 사용자 조회
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다"));
        
        // 2. 비밀번호 검증 (나중에 암호화 적용 예정)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
        }

        // 3. JWT 토큰 생성 
        String accessToken = jwtUtil.generateToken(user.getId(), user.getEmail());
        
        
        // 4. 로그인 성공 응답 생성
        return LoginResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .message("로그인 성공")
                .accessToken(accessToken)
                .build();
    }

    // 로그아웃
    public void logout(String token) {
        // 1. 토큰 유효성 검증
        if (!jwtUtil.validateToken(token)) {
            log.warn("유효하지 않은 토큰으로 로그아웃 시도");
            throw new IllegalArgumentException("유효하지 않은 토큰입니다");
        }
        
        // 2. 이미 블랙리스트에 있는지 확인
        if (blacklistService.isBlacklisted(token)) {
            log.warn("이미 로그아웃된 토큰");
            throw new IllegalArgumentException("이미 로그아웃된 토큰입니다");
        }
        
        // 3. 토큰의 남은 만료 시간 계산
        long expirationTimeMs = jwtUtil.getExpirationTimeMs(token);
        
        if (expirationTimeMs <= 0) {
            log.warn("이미 만료된 토큰으로 로그아웃 시도");
            throw new IllegalArgumentException("이미 만료된 토큰입니다");
        }
        
        // 4. Redis 블랙리스트에 토큰 추가
        blacklistService.addToBlacklist(token, expirationTimeMs);
        
        log.info("로그아웃 성공 - 토큰 블랙리스트 등록 완료 (TTL: {}ms)", expirationTimeMs);
    }

    // 회원가입(이메일 인증 필수)
    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        // 1. 이메일 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다");
        }
        // 2. 이메일 인증 완료 여부 확인
        if (!emailVerificationService.isEmailVerified(request.getEmail())) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다. 먼저 이메일 인증을 진행해주세요.");
        }
        // 3. 사용자 엔티티 생성 (나중에 비밀번호 암호화 추가 예정)
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))  // ✨ 암호화
                .name(request.getName())
                .phone(request.getPhone())
                .build();
        
        // 4. DB에 저장
        User savedUser = userRepository.save(user);
        
        // 5. 인증 완료 플래그 삭제 (회원가입 완료되었으므로 더 이상 불필요)
        emailVerificationService.clearVerifiedFlag(request.getEmail());
        
        log.info("회원가입 성공 - 이메일: {}, 사용자 ID: {}", savedUser.getEmail(), savedUser.getId());
        

        // 6. 응답 생성
        return SignUpResponse.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .name(savedUser.getName())
                .phone(savedUser.getPhone())
                .createdAt(savedUser.getCreatedAt())
                .message("회원가입 성공")
                .build();
    }

    
}