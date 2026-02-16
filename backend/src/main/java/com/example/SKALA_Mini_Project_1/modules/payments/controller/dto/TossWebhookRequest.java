package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import lombok.Getter;

@Getter
public class TossWebhookRequest {

    private String paymentKey;
    private String orderId;
    private String status;
    private Long totalAmount;
}
