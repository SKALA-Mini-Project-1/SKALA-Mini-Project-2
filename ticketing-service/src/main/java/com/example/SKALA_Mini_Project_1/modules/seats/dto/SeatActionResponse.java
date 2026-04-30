package com.example.SKALA_Mini_Project_1.modules.seats.dto;

public record SeatActionResponse(
        String status,
        String message,
        String action
) {
    public static SeatActionResponse success(String message, String action) {
        return new SeatActionResponse("success", message, action);
    }

    public static SeatActionResponse failure(String status, String message) {
        return new SeatActionResponse(status, message, null);
    }
}
