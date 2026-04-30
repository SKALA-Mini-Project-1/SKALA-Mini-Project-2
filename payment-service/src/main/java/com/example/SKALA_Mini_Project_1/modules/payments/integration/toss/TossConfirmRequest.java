package com.example.SKALA_Mini_Project_1.modules.payments.integration.toss;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TossConfirmRequest {

    private String paymentKey;
    private String orderId;
    private Long amount;
}
