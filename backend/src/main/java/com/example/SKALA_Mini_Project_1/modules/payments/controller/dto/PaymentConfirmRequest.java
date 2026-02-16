package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import lombok.Getter;

@Getter
public class PaymentConfirmRequest {
    private String paymentKey;
    private String orderId;
    private Long amount;
}