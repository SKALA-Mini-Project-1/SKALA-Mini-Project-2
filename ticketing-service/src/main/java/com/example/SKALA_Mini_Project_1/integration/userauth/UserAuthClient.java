package com.example.SKALA_Mini_Project_1.integration.userauth;

import com.example.SKALA_Mini_Project_1.modules.fanscore.exception.FanScoreSyncException;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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

    public InternalUserProfileResponse getUserProfile(Long userId) {
        try {
            InternalUserProfileResponse response = restClient.get()
                    .uri("/internal/users/{userId}", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, responseSpec) -> {
                        throw new EntityNotFoundException("사용자 정보를 찾을 수 없습니다.");
                    })
                    .body(InternalUserProfileResponse.class);
            if (response == null) {
                throw new EntityNotFoundException("사용자 정보를 찾을 수 없습니다.");
            }
            return response;
        } catch (RestClientResponseException e) {
            throw new EntityNotFoundException("사용자 정보를 찾을 수 없습니다.");
        }
    }

    public void applyAttendanceConfirmedFanScore(
            Long userId,
            UUID bookingId,
            Long concertId,
            Long artistId,
            OffsetDateTime occurredAt
    ) {
        try {
            restClient.post()
                    .uri("/internal/fan-scores/events/attendance-confirmed")
                    .body(new InternalFanScoreApplyRequest(userId, bookingId, concertId, artistId, occurredAt))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new FanScoreSyncException("user-auth-service Fan Score 반영에 실패했습니다.");
                    })
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new FanScoreSyncException("user-auth-service Fan Score 반영에 실패했습니다.", e);
        } catch (RestClientException e) {
            throw new FanScoreSyncException("user-auth-service 연결에 실패했습니다.", e);
        }
    }
}
