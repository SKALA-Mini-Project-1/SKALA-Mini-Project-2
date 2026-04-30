package com.example.SKALA_Mini_Project_1.modules.finalization.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class InternalApiGuard {

    public static final String HEADER_NAME = "X-Internal-Api-Key";

    private final String internalApiToken;

    public InternalApiGuard(@Value("${ticketing.internal-api.token}") String internalApiToken) {
        this.internalApiToken = internalApiToken;
    }

    public void validate(String apiKey) {
        if (apiKey == null || apiKey.isBlank() || !internalApiToken.equals(apiKey)) {
            throw new AccessDeniedException("유효하지 않은 내부 API 인증 정보입니다.");
        }
    }
}
