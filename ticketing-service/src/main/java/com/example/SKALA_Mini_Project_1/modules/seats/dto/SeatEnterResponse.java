package com.example.SKALA_Mini_Project_1.modules.seats.dto;

public record SeatEnterResponse(
        String status,
        String message
) {
    public static SeatEnterResponse success(String message) {
        return new SeatEnterResponse("success", message);
    }

    public static SeatEnterResponse forbidden(String message) {
        return new SeatEnterResponse("forbidden", message);
    }
}
