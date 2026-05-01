package com.example.SKALA_Mini_Project_1.modules.seats.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
@Schema(description = "스케줄별 좌석 구역 요약 응답")
public class SeatSectionSummaryResponse {

    @Schema(description = "콘서트 ID", example = "1")
    private Long concertId;

    @Schema(description = "스케줄 ID", example = "1")
    private Long scheduleId;

    @Schema(description = "전체 좌석 수", example = "14000")
    private int totalSeatCount;

    @Schema(description = "구역 수", example = "26")
    private int sectionCount;

    @Schema(description = "좌석 화면 접근 가능 남은 시간(초)", example = "300")
    private Long seatAccessTtlSeconds;

    @Schema(description = "구역 목록")
    private List<SectionSummaryItem> sections;

    @Getter
    @Builder
    @Schema(description = "구역 요약 정보")
    public static class SectionSummaryItem {
        @Schema(description = "구역 ID", example = "A")
        private String section;

        @Schema(description = "구역 총 좌석 수", example = "700")
        private int seatCount;

        @Schema(description = "구역 내 예매 완료 좌석 수", example = "120")
        private int reservedSeatCount;

        @Schema(description = "구역 내 현재 AVAILABLE 좌석 수", example = "580")
        private int availableSeatCount;

        @Schema(description = "구역 열 수", example = "25")
        private int rowCount;

        @Schema(description = "구역 행당 최대 좌석 수", example = "28")
        private int colCount;

        @Schema(description = "구역 대표 등급", example = "VIP")
        private String grade;

        @Schema(description = "구역 대표 가격", example = "165000")
        private BigDecimal price;
    }
}
