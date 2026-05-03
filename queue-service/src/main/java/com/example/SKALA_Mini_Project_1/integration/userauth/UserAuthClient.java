package com.example.SKALA_Mini_Project_1.integration.userauth;

import com.example.SKALA_Mini_Project_1.modules.waiting.exception.DownstreamServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
            if (e.getStatusCode().value() == 404) {
                throw new IllegalArgumentException("사용자 없음", e);
            }
            throw new DownstreamServiceException("user-auth-service 응답을 확인할 수 없습니다.", e);
        } catch (RestClientException e) {
            throw new DownstreamServiceException("user-auth-service 연결에 실패했습니다.", e);
        }
    }

    public int getArtistFanScore(Long userId, Long artistId) {
        try {
            InternalArtistFanScoreResponse response = restClient.get()
                    .uri("/internal/fan-scores/users/{userId}/artists/{artistId}", userId, artistId)
                    .retrieve()
                    .onStatus(
                            status -> status.value() == HttpStatus.NOT_FOUND.value(),
                            (request, responseSpec) -> {
                                throw new IllegalArgumentException("Fan Score 정보를 찾을 수 없습니다.");
                            }
                    )
                    .onStatus(HttpStatusCode::isError, (request, responseSpec) -> {
                        throw new DownstreamServiceException("user-auth-service 응답을 확인할 수 없습니다.");
                    })
                    .body(InternalArtistFanScoreResponse.class);
            if (response == null) {
                return 0;
            }
            return Math.max(0, response.totalScore());
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new IllegalArgumentException("Fan Score 정보를 찾을 수 없습니다.", e);
            }
            throw new DownstreamServiceException("user-auth-service 응답을 확인할 수 없습니다.", e);
        } catch (RestClientException e) {
            throw new DownstreamServiceException("user-auth-service 연결에 실패했습니다.", e);
        }
    }
}
