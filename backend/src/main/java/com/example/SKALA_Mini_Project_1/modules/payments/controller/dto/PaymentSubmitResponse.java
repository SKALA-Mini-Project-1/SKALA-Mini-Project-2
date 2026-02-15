package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.SKALA_Mini_Project_1.modules.payments.domain.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;



@Getter
@AllArgsConstructor
public class PaymentSubmitResponse {

    private UUID id;
    private String status;
    private OffsetDateTime expiredAt;
    private String idempotencyKey;
    private OffsetDateTime updatedAt;

    private UUID bookingId;

    private BigDecimal amount;

    // PG 위젯 호출용 값들
    private String orderId;
    private String customerKey;
    private String orderName;
    private String successUrl;
    private String failUrl;
}
