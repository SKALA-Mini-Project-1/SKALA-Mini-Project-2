package com.example.SKALA_Mini_Project_1.modules.users.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationResponse {
    
    private String email;
    private String message;
    private Integer expirationMinutes;  // 인증 코드 유효 시간 (분)
    private Long resendAvailableAt;      // 재발송 가능 시간 (Unix timestamp, 초 단위)
}