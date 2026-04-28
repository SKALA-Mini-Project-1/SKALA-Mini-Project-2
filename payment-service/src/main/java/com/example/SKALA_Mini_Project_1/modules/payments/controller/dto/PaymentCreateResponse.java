package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

// 결제 생성 결과로 paymentId와 상태/만료시각을 반환한다

import java.time.OffsetDateTime;
import java.util.UUID;

import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class PaymentCreateResponse {

    private UUID paymentId;
    private PaymentStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiredAt;

}
