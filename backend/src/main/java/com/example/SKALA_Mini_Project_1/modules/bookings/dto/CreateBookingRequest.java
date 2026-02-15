package com.example.SKALA_Mini_Project_1.modules.bookings.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "예약 생성 요청 DTO")
public class CreateBookingRequest {

    @NotNull(message = "콘서트 ID는 필수입니다.")
    @Schema(description = "콘서트 ID", example = "1")
    private Long concertId;

    @NotEmpty(message = "좌석 ID 목록은 비어 있을 수 없습니다.")
    @Size(max = 4, message = "좌석은 최대 4매까지만 선택할 수 있습니다.")
    @ArraySchema(
            schema = @Schema(description = "예약할 좌석 ID", example = "401"),
            arraySchema = @Schema(description = "예약할 좌석 ID 목록(최대 4개)")
    )
    private List<Long> seatIds;
}
