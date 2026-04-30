package com.example.SKALA_Mini_Project_1.modules.seats.dto;

import java.util.List;

public record SeatBatchHoldResponse(
        String status,
        String message,
        List<Long> heldSeatIds,
        List<Long> failedSeatIds
) {
    public static SeatBatchHoldResponse success(String message, List<Long> heldSeatIds) {
        return new SeatBatchHoldResponse("success", message, heldSeatIds, List.of());
    }

    public static SeatBatchHoldResponse conflict(String message, List<Long> failedSeatIds) {
        return new SeatBatchHoldResponse("conflict", message, List.of(), failedSeatIds);
    }

    public static SeatBatchHoldResponse badRequest(String message) {
        return new SeatBatchHoldResponse("bad_request", message, List.of(), List.of());
    }
}
