package com.example.SKALA_Mini_Project_1.modules.payments.integration.toss;

import lombok.Getter;

@Getter
public class TossCancelResponse {

    private String paymentKey;
    private String orderId;
    private String status;
}
