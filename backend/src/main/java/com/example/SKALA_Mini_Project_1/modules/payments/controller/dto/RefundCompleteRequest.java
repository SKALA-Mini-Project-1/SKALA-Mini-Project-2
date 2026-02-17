package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefundCompleteRequest {
    // CANCELED 또는 REFUNDED, 미지정 시 REFUNDED
    private String paymentStatus;
    private String pgRefundId;
}
