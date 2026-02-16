package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentConfirmResponse {
    private UUID paymentId;
    private UUID bookingId;
    private String status;
}