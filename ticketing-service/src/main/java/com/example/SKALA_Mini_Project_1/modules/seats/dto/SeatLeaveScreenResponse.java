package com.example.SKALA_Mini_Project_1.modules.seats.dto;

public record SeatLeaveScreenResponse(
        String status,
        String message,
        int releasedSeatCount,
        boolean activeDecremented,
        long activeCount
) {
    public static SeatLeaveScreenResponse success(
            int releasedSeatCount,
            boolean activeDecremented,
            long activeCount
    ) {
        return new SeatLeaveScreenResponse(
                "success",
                "좌석 선택 화면을 정상적으로 이탈했습니다.",
                releasedSeatCount,
                activeDecremented,
                activeCount
        );
    }

    public static SeatLeaveScreenResponse badRequest(String message) {
        return new SeatLeaveScreenResponse("bad_request", message, 0, false, -1L);
    }
}
