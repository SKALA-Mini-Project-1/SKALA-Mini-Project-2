package com.example.SKALA_Mini_Project_1.modules.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "내 정보 수정 요청")
public class UpdateMyInfoRequest {

    @NotBlank(message = "이름은 필수입니다")
    @Schema(description = "이름", example = "홍길동")
    private String name;

    @NotBlank(message = "전화번호는 필수입니다")
    @Pattern(regexp = "^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$", message = "올바른 전화번호 형식이 아닙니다")
    @Schema(description = "전화번호", example = "010-1234-5678")
    private String phone;
}
