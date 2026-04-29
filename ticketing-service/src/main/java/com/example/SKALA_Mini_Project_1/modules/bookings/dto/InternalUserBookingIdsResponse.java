package com.example.SKALA_Mini_Project_1.modules.bookings.dto;

import java.util.List;
import java.util.UUID;

public record InternalUserBookingIdsResponse(
        Long userId,
        List<UUID> bookingIds
) {
}
