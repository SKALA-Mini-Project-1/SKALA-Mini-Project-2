package com.example.SKALA_Mini_Project_1.modules.users.dto;

public record MyInfoResponse(
        Long userId,
        String email,
        String name,
        String phone,
        int fanScore,
        String message
) {
    public static MyInfoResponse of(
            Long userId,
            String email,
            String name,
            String phone,
            int fanScore,
            String message
    ) {
        return new MyInfoResponse(userId, email, name, phone, fanScore, message);
    }
}
