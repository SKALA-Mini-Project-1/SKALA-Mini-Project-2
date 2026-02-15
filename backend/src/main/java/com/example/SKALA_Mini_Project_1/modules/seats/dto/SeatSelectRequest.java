package com.example.SKALA_Mini_Project_1.modules.seats.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(
        description = "좌석 선점/해제 요청 DTO"
)
public class SeatSelectRequest {

    @NotNull(message = "콘서트 ID는 필수입니다.")
    @Schema(description = "콘서트 ID", example = "1")
    private Long concertId;

    @NotNull(message = "좌석 ID는 필수입니다.")
    @Schema(description = "좌석 PK ID", example = "401")
    private Long seatId;

    public SeatSelectRequest(Long concertId, Long seatId) {
        this.concertId = concertId;
        this.seatId = seatId;
    }
}
