package com.example.SKALA_Mini_Project_1.modules.seats.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "콘서트 좌석 맵 조회 응답")
public class SeatMapResponse {

    @Schema(description = "콘서트 ID", example = "1")
    private Long concertId;

    @Schema(description = "총 좌석 수", example = "14000")
    private int seatCount;

    @Schema(description = "좌석 목록")
    private List<SeatItem> seats;

    @Getter
    @Builder
    @Schema(description = "좌석 정보")
    public static class SeatItem {
        @Schema(description = "좌석 ID", example = "1")
        private Long seatId;

        @Schema(description = "구역", example = "A")
        private String section;

        @Schema(description = "열 번호", example = "1")
        private Integer rowNumber;

        @Schema(description = "좌석 번호", example = "1")
        private Integer seatNumber;

        @Schema(description = "좌석 상태", example = "AVAILABLE")
        private String status;

        @Schema(description = "좌석 등급", example = "VIP")
        private String grade;

        @Schema(description = "좌석 가격", example = "180000")
        private BigDecimal price;

        @Schema(description = "요청 사용자 기준 선점 여부", nullable = true, example = "false")
        private Boolean isHeldByMe;

        @Schema(description = "Redis 선점 만료 시각(UTC)", nullable = true)
        private OffsetDateTime holdExpiresAt;
    }
}
