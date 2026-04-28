package com.example.SKALA_Mini_Project_1.modules.users.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class SignUpResponse {
    
    private Long userId;
    private String email;
    private String name;
    private String phone;
    private LocalDateTime createdAt;
    private String message;
}