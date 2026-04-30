package com.example.SKALA_Mini_Project_1.integration.concert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ConcertServiceClient {

    private static final String INTERNAL_API_HEADER_NAME = "X-Internal-Api-Key";

    private final RestClient restClient;

    public ConcertServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${concert-service.base-url}") String concertServiceBaseUrl,
            @Value("${concert.internal-api.token}") String internalApiToken
    ) {
        this.restClient = restClientBuilder
                .baseUrl(concertServiceBaseUrl)
                .defaultHeader(INTERNAL_API_HEADER_NAME, internalApiToken)
                .build();
    }

    public void ensureScheduleBelongsToConcert(Long concertId, Long scheduleId) {
        try {
            restClient.get()
                    .uri("/internal/concerts/{concertId}/schedules/{scheduleId}", concertId, scheduleId)
                    .retrieve()
                    .onStatus(
                            status -> status.value() == HttpStatus.NOT_FOUND.value(),
                            (request, response) -> {
                                throw new IllegalArgumentException(
                                        "회차를 찾을 수 없습니다. concertId=" + concertId + ", scheduleId=" + scheduleId
                                );
                            }
                    )
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalStateException(
                                "Concert schedule validation failed with status: " + response.getStatusCode()
                        );
                    })
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new IllegalArgumentException(
                        "회차를 찾을 수 없습니다. concertId=" + concertId + ", scheduleId=" + scheduleId,
                        e
                );
            }
            throw new IllegalStateException("Concert schedule validation failed: " + e.getStatusCode(), e);
        }
    }

    public Long getArtistIdForConcert(Long concertId) {
        try {
            return restClient.get()
                    .uri("/internal/concerts/{concertId}/artist-id", concertId)
                    .retrieve()
                    .onStatus(
                            status -> status.value() == HttpStatus.NOT_FOUND.value(),
                            (request, response) -> {
                                throw new IllegalArgumentException(
                                        "콘서트 아티스트를 찾을 수 없습니다. concertId=" + concertId
                                );
                            }
                    )
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalStateException(
                                "Concert artist lookup failed with status: " + response.getStatusCode()
                        );
                    })
                    .body(Long.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new IllegalArgumentException(
                        "콘서트 아티스트를 찾을 수 없습니다. concertId=" + concertId,
                        e
                );
            }
            throw new IllegalStateException("Concert artist lookup failed: " + e.getStatusCode(), e);
        }
    }
}
