package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentExitSignalRequest {
    private String reasonCode;
    private String source;
    private String clientRoute;
}
