package com.example.SKALA_Mini_Project_1.modules.payments.integration.toss;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.SKALA_Mini_Project_1.modules.payments.exception.TossPaymentsException;

@Component
public class TossPaymentsClient {

    private final WebClient webClient;
    private final String confirmUrl;
    private final String cancelBaseUrl;

    public TossPaymentsClient(
            WebClient.Builder webClientBuilder,
            @Value("${toss.secret-key}") String secretKey,
            @Value("${toss.confirm-url}") String confirmUrl,
            @Value("${toss.cancel-base-url:https://api.tosspayments.com/v1/payments}") String cancelBaseUrl
    ) {
        this.confirmUrl = confirmUrl;
        this.cancelBaseUrl = cancelBaseUrl;

        String credential = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));

        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credential)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public TossConfirmResponse confirm(String paymentKey, String orderId, Long amount) {
        try {
            return webClient.post()
                    .uri(confirmUrl)
                    .bodyValue(new TossConfirmRequest(paymentKey, orderId, amount))
                    .retrieve()
                    .bodyToMono(TossConfirmResponse.class)
                    .block();

        } catch (WebClientResponseException e) {
            throw new TossPaymentsException(
                    "Toss confirm failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(),
                    e
            );
        }
    }

    public TossCancelResponse cancel(String paymentKey, Long cancelAmount, String cancelReason) {
        try {
            return webClient.post()
                    .uri(cancelBaseUrl + "/" + paymentKey + "/cancel")
                    .bodyValue(new TossCancelRequest(cancelReason, cancelAmount))
                    .retrieve()
                    .bodyToMono(TossCancelResponse.class)
                    .block();

        } catch (WebClientResponseException e) {
            throw new TossPaymentsException(
                    "Toss cancel failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(),
                    e
            );
        }
    }
}
