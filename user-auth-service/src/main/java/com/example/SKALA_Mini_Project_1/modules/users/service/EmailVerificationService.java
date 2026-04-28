package com.example.SKALA_Mini_Project_1.modules.users.service;

import java.security.SecureRandom;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.SKALA_Mini_Project_1.global.email.EmailProperties;
import com.example.SKALA_Mini_Project_1.global.email.EmailService;
import com.example.SKALA_Mini_Project_1.global.redis.RedisEmailVerificationRepository;
import com.example.SKALA_Mini_Project_1.global.util.EmailValidator;
import com.example.SKALA_Mini_Project_1.modules.users.UserRepository;
import com.example.SKALA_Mini_Project_1.modules.users.dto.EmailVerificationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {
    
    private final EmailService emailService;
    private final RedisEmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final EmailProperties emailProperties;
    
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long VERIFIED_FLAG_EXPIRATION_MINUTES = 30;
    
    /**
     * 이메일 인증 코드 발송
     */
    public EmailVerificationResponse sendVerificationCode(String email) {
      EmailValidator.validate(email);
        // 1. 이미 가입된 이메일인지 확인
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다");
        }
        
        // 2. 재발송 제한 확인
        if (!verificationRepository.canResend(email)) {
            Long resendAvailableAt = verificationRepository.getResendAvailableTime(email);
            throw new IllegalArgumentException(
                String.format("인증 코드는 1분에 한 번만 발송할 수 있습니다. %d초 후 재시도해주세요.", 
                    resendAvailableAt - (System.currentTimeMillis() / 1000))
            );
        }
        
        // 3. 인증 코드 생성
        String code = generateVerificationCode();
        
        // 4. Redis에 저장
        int expirationMinutes = emailProperties.getVerification().getExpirationMinutes();
        verificationRepository.saveVerificationCode(email, code, expirationMinutes);
        
        // 5. 재발송 제한 설정
        int resendIntervalSeconds = emailProperties.getVerification().getResendIntervalSeconds();
        verificationRepository.setResendLimit(email, resendIntervalSeconds);
        
        // 6. 이메일 발송
        try {
            emailService.sendVerificationCode(email, code);
        } catch (Exception e) {
            log.error("이메일 발송 실패 - 이메일: {}", email, e);
            verificationRepository.deleteVerificationCode(email);
            throw new RuntimeException("이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
        
        // 7. 응답 생성
        Long resendAvailableAt = verificationRepository.getResendAvailableTime(email);
        
        return EmailVerificationResponse.builder()
                .email(email)
                .message("인증 코드가 이메일로 발송되었습니다")
                .expirationMinutes(expirationMinutes)
                .resendAvailableAt(resendAvailableAt)
                .build();
    }
    
    /**
     * 이메일 인증 코드 검증
     */
    public EmailVerificationResponse verifyCode(String email, String code) {
      EmailValidator.validate(email);
        // 1. Redis에서 저장된 인증 코드 조회
        Optional<String> savedCode = verificationRepository.getVerificationCode(email);
        
        if (savedCode.isEmpty()) {
            throw new IllegalArgumentException("인증 코드가 만료되었거나 존재하지 않습니다. 다시 요청해주세요.");
        }
        
        // 2. 코드 일치 여부 확인
        if (!savedCode.get().equals(code)) {
            throw new IllegalArgumentException("인증 코드가 일치하지 않습니다");
        }
        
        // 3. 인증 성공 - Redis에서 코드 삭제
        verificationRepository.deleteVerificationCode(email);
        
        // 4. 인증 완료 플래그 저장
        verificationRepository.markAsVerified(email, VERIFIED_FLAG_EXPIRATION_MINUTES);
        
        log.info("이메일 인증 성공 - 이메일: {}", email);
        
        // 5. 응답 생성
        return EmailVerificationResponse.builder()
                .email(email)
                .message("이메일 인증이 완료되었습니다")
                .expirationMinutes(null)
                .resendAvailableAt(null)
                .build();
    }
    
    /**
     * 이메일 인증 여부 확인
     */
    public boolean isEmailVerified(String email) {
        return verificationRepository.isVerified(email);
    }
    
    /**
     * 인증 완료 플래그 삭제
     */
    public void clearVerifiedFlag(String email) {
        verificationRepository.deleteVerifiedFlag(email);
        log.info("회원가입 완료 - 인증 완료 플래그 삭제: {}", email);
    }
    
    /**
     * 6자리 랜덤 숫자 인증 코드 생성
     */
    private String generateVerificationCode() {
        int codeLength = emailProperties.getVerification().getCodeLength();
        int bound = (int) Math.pow(10, codeLength);
        int code = RANDOM.nextInt(bound);
        return String.format("%0" + codeLength + "d", code);
    }
}