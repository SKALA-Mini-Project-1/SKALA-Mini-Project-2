package com.example.SKALA_Mini_Project_1.integration.userauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class UserAuthClient {

    private static final String INTERNAL_API_HEADER_NAME = "X-Internal-Api-Key";

    private final RestClient restClient;

    public UserAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${user-auth-service.base-url}") String userAuthServiceBaseUrl,
            @Value("${user-auth.internal-api.token}") String internalApiToken
    ) {
        this.restClient = restClientBuilder
                .baseUrl(userAuthServiceBaseUrl)
                .defaultHeader(INTERNAL_API_HEADER_NAME, internalApiToken)
                .build();
    }

    public void ensureUserExists(Long userId) {
        try {
            restClient.get()
                    .uri("/internal/users/{userId}", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalArgumentException("사용자 없음");
                    })
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new IllegalArgumentException("사용자 없음", e);
        }
    }
}
