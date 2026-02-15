package com.example.SKALA_Mini_Project_1.modules.bookings.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@Schema(description = "예약 생성 응답 DTO")
public class CreateBookingResponse {

    @Schema(description = "예약 ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID bookingId;

    @Schema(description = "예약 상태", example = "HOLDING")
    private String status;

    @Schema(description = "결제 가능 만료 시각(UTC)")
    private OffsetDateTime expiresAt;

    @Schema(description = "총 결제 금액", example = "560000")
    private BigDecimal totalPrice;

    @Schema(description = "예약된 좌석 ID 목록")
    private List<Long> seatIds;
}
