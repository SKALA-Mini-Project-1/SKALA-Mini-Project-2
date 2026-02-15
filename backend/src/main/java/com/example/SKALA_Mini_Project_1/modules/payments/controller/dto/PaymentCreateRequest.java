// 결제 생성에 필요한 최소 입력값을 받는다

package com.example.SKALA_Mini_Project_1.modules.payments.controller.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class PaymentCreateRequest {

    private UUID bookingId;

    @NotNull
    private Long userId;

    @NotNull
    private Long seatId;

    @NotNull
    private BigDecimal amount;

}
