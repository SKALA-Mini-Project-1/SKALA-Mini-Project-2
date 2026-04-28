package com.example.SKALA_Mini_Project_1.global.util;

import java.util.regex.Pattern;

public class EmailValidator {
    
    // RFC 5322 기반 이메일 정규식 (간소화 버전)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    
    // 허용되지 않는 도메인 (임시 이메일 등)
    private static final String[] BLOCKED_DOMAINS = {
        "tempmail.com",
        "throwaway.email",
        "guerrillamail.com",
        "10minutemail.com",
        "mailinator.com"
    };
    
    /**
     * 이메일 형식 유효성 검증
     * @param email 검증할 이메일 주소
     * @return true: 유효한 이메일, false: 유효하지 않은 이메일
     */
    public static boolean isValid(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        email = email.trim().toLowerCase();
        
        // 1. 기본 형식 검증
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return false;
        }
        
        // 2. 길이 검증
        if (email.length() > 254) { // RFC 5321
            return false;
        }
        
        // 3. 로컬 파트 검증 (@ 앞부분)
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return false;
        }
        
        String localPart = parts[0];
        String domain = parts[1];
        
        if (localPart.length() > 64) { // RFC 5321
            return false;
        }
        
        // 4. 도메인 검증
        if (domain.length() < 4) { // 최소 a.co 형태
            return false;
        }
        
        // 5. 차단된 도메인 검증
        for (String blockedDomain : BLOCKED_DOMAINS) {
            if (domain.equals(blockedDomain)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 이메일 형식 검증 및 예외 발생
     * @param email 검증할 이메일 주소
     * @throws IllegalArgumentException 유효하지 않은 이메일인 경우
     */
    public static void validate(String email) {
        if (!isValid(email)) {
            throw new IllegalArgumentException("유효하지 않은 이메일 형식입니다");
        }
    }
}