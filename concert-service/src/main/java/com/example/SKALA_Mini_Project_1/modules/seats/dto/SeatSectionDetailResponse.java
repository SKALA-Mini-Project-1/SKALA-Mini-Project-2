package com.example.SKALA_Mini_Project_1.modules.seats.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "스케줄별 구역 상세 좌석 응답")
public class SeatSectionDetailResponse {

    @Schema(description = "콘서트 ID", example = "1")
    private Long concertId;

    @Schema(description = "스케줄 ID", example = "1")
    private Long scheduleId;

    @Schema(description = "구역 ID", example = "A")
    private String section;

    @Schema(description = "구역 좌석 수", example = "700")
    private int seatCount;

    @Schema(description = "좌석 화면 접근 가능 남은 시간(초)", example = "300")
    private Long seatAccessTtlSeconds;

    @Schema(description = "구역 좌석 목록")
    private List<SeatMapResponse.SeatItem> seats;
}
