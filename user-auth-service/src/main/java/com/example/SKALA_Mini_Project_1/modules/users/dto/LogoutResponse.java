package com.example.SKALA_Mini_Project_1.modules.users.dto;

public record LogoutResponse(
        String message,
        String status
) {
    public static LogoutResponse success() {
        return new LogoutResponse("로그아웃 성공", "success");
    }
}
