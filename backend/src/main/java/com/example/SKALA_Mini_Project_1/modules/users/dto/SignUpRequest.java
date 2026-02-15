package com.example.SKALA_Mini_Project_1.modules.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "회원가입 요청")

public class SignUpRequest {
    
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Pattern(
        regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
        message = "올바른 이메일 형식이 아닙니다 (예: example@domain.com)"
    )
    @Schema(description = "이메일", example = "user@example.com")
    private String email;
    
    @NotBlank(message = "비밀번호는 필수입니다")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
    @Schema(description = "비밀번호 (최소 8자)", example = "password123")
    private String password;
    
    @NotBlank(message = "이름은 필수입니다")
    @Schema(description = "이름", example = "홍길동")
    private String name;
    
    @Pattern(regexp = "^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$", message = "올바른 전화번호 형식이 아닙니다")
    @Schema(description = "전화번호 (선택사항)", example = "010-1234-5678")
    private String phone;
}
