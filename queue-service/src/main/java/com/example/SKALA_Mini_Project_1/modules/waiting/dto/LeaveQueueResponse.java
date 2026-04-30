package com.example.SKALA_Mini_Project_1.modules.waiting.dto;

public record LeaveQueueResponse(
        String status,
        boolean removed
) {
    public static LeaveQueueResponse success(boolean removed) {
        return new LeaveQueueResponse("success", removed);
    }
}
