package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class PaymentGetResponse {
    private UUID id;
    private UUID bookingId;
    private Long amount;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiredAt;
    private OffsetDateTime updatedAt;
    private String idempotencyKey;
}
