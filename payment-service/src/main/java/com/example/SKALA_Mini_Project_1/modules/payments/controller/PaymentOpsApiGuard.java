package com.example.SKALA_Mini_Project_1.modules.payments.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class PaymentOpsApiGuard {

    public static final String HEADER_NAME = "X-Internal-Api-Key";

    private final String opsApiToken;

    public PaymentOpsApiGuard(@Value("${payment.ops-api.token}") String opsApiToken) {
        this.opsApiToken = opsApiToken;
    }

    public void validate(String apiKey) {
        if (apiKey == null || apiKey.isBlank() || !opsApiToken.equals(apiKey)) {
            throw new AccessDeniedException("유효하지 않은 운영 API 인증 정보입니다.");
        }
    }
}
