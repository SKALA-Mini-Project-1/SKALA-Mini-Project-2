package com.example.SKALA_Mini_Project_1.modules.seats.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(
        description = "좌석 선점/해제 요청 DTO"
)
public class SeatSelectRequest {

    @NotNull(message = "스케줄 ID는 필수입니다.")
    @Schema(description = "스케줄 ID", example = "1")
    private Long scheduleId;

    @NotBlank(message = "구역(section)은 필수입니다.")
    @Schema(description = "구역 코드", example = "A")
    private String section;

    @NotNull(message = "열 번호(rowNumber)는 필수입니다.")
    @Schema(description = "열 번호", example = "1")
    private Integer rowNumber;

    @NotNull(message = "좌석 번호(seatNumber)는 필수입니다.")
    @Schema(description = "좌석 번호", example = "1")
    private Integer seatNumber;

    public SeatSelectRequest(Long scheduleId, String section, Integer rowNumber, Integer seatNumber) {
        this.scheduleId = scheduleId;
        this.section = section;
        this.rowNumber = rowNumber;
        this.seatNumber = seatNumber;
    }
}
