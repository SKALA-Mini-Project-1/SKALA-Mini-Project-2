package com.example.SKALA_Mini_Project_1.modules.users.dto;

public record InternalUserProfileResponse(
        Long userId,
        String email,
        String name,
        String phone
) {
}
