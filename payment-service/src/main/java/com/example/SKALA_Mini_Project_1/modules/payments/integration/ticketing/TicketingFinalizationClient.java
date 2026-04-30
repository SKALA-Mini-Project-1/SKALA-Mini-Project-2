package com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing;

import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.example.SKALA_Mini_Project_1.modules.payments.domain.Payment;

@Component
public class TicketingFinalizationClient {

    public static final String HEADER_NAME = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final String finalizationPath;
    private final String internalApiToken;

    public TicketingFinalizationClient(
            RestClient.Builder restClientBuilder,
            @Value("${ticketing-service.base-url}") String ticketingServiceBaseUrl,
            @Value("${ticketing-service.finalization-path}") String finalizationPath,
            @Value("${ticketing-service.internal-api-token}") String internalApiToken
    ) {
        this.restClient = restClientBuilder
                .baseUrl(ticketingServiceBaseUrl)
                .build();
        this.finalizationPath = finalizationPath;
        this.internalApiToken = internalApiToken;
    }

    public InternalBookingFinalizationResponse confirmBooking(Payment payment, OffsetDateTime confirmedAt) {
        InternalBookingConfirmRequest request = new InternalBookingConfirmRequest(
                payment.getId(),
                payment.getPgOrderId(),
                payment.getPgPaymentKey(),
                payment.getAmount(),
                confirmedAt,
                payment.getBookingId()
        );
        return post("/confirm", request);
    }

    public InternalBookingFinalizationResponse cancelBooking(Payment payment, OffsetDateTime canceledAt, String reasonCode) {
        InternalBookingCancelRequest request = new InternalBookingCancelRequest(
                payment.getId(),
                payment.getPgOrderId(),
                reasonCode,
                canceledAt,
                payment.getBookingId()
        );
        return post("/cancel", request);
    }

    public InternalBookingFinalizationResponse expireBooking(Payment payment, OffsetDateTime expiredAt, String reasonCode) {
        InternalBookingExpireRequest request = new InternalBookingExpireRequest(
                payment.getId(),
                payment.getPgOrderId(),
                reasonCode,
                expiredAt,
                payment.getBookingId()
        );
        return post("/expire", request);
    }

    public InternalBookingFinalizationResponse finalizeBooking(
            Payment payment,
            TicketingFinalizationAction action,
            OffsetDateTime occurredAt,
            String reasonCode
    ) {
        return switch (action) {
            case CONFIRM -> confirmBooking(payment, occurredAt);
            case CANCEL -> cancelBooking(payment, occurredAt, reasonCode);
            case EXPIRE -> expireBooking(payment, occurredAt, reasonCode);
            case FAIL -> cancelBooking(payment, occurredAt, reasonCode);
        };
    }

    private InternalBookingFinalizationResponse post(String suffix, Object request) {
        try {
            return restClient.post()
                    .uri(finalizationPath + suffix)
                    .header(HEADER_NAME, internalApiToken)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (clientRequest, clientResponse) -> {
                        throw new IllegalStateException(
                                "Ticketing finalization failed with status: " + clientResponse.getStatusCode()
                        );
                    })
                    .body(InternalBookingFinalizationResponse.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "Ticketing finalization failed: " + e.getStatusCode(),
                    e
            );
        }
    }
}
