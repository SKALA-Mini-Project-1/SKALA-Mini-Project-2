package com.example.SKALA_Mini_Project_1.modules.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @Schema(description = "이메일", example = "user@example.com")
    private String email;
    
    @NotBlank(message = "비밀번호는 필수입니다")
    @Schema(description = "비밀번호 (최소 8자)", example = "password123")
    private String password;
}