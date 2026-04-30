package com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TicketingBookingQueryClient {

    public static final String HEADER_NAME = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String internalApiToken;

    public TicketingBookingQueryClient(
            RestClient.Builder restClientBuilder,
            @Value("${ticketing-service.base-url}") String ticketingServiceBaseUrl,
            @Value("${ticketing-service.internal-api-token}") String internalApiToken
    ) {
        this.restClient = restClientBuilder
                .baseUrl(ticketingServiceBaseUrl)
                .build();
        this.internalApiToken = internalApiToken;
    }

    public InternalBookingPaymentContextResponse getPaymentContext(UUID bookingId) {
        try {
            return restClient.get()
                    .uri("/internal/bookings/{bookingId}/payment-context", bookingId)
                    .header(HEADER_NAME, internalApiToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalStateException(
                                "Ticketing booking query failed with status: " + response.getStatusCode()
                        );
                    })
                    .body(InternalBookingPaymentContextResponse.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Ticketing booking query failed: " + e.getStatusCode(), e);
        }
    }

    public InternalBookingHistoryDetailsResponse getHistoryDetails(List<UUID> bookingIds) {
        try {
            return restClient.post()
                    .uri("/internal/bookings/history-details")
                    .header(HEADER_NAME, internalApiToken)
                    .body(new InternalBookingHistoryDetailsRequest(bookingIds))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalStateException(
                                "Ticketing booking history query failed with status: " + response.getStatusCode()
                        );
                    })
                    .body(InternalBookingHistoryDetailsResponse.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Ticketing booking history query failed: " + e.getStatusCode(), e);
        }
    }

    public InternalUserBookingIdsResponse getUserBookingIds(Long userId) {
        try {
            return restClient.get()
                    .uri("/internal/bookings/users/{userId}/ids", userId)
                    .header(HEADER_NAME, internalApiToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalStateException(
                                "Ticketing user booking ids query failed with status: " + response.getStatusCode()
                        );
                    })
                    .body(InternalUserBookingIdsResponse.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Ticketing user booking ids query failed: " + e.getStatusCode(), e);
        }
    }
}
