package com.example.SKALA_Mini_Project_1.modules.payments.integration.toss;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TossCancelRequest {

    private String cancelReason;
    private Long cancelAmount;
}
