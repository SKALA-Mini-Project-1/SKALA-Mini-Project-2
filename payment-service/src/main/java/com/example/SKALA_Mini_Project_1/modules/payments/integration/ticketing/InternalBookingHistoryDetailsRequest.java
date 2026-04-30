package com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing;

import java.util.List;
import java.util.UUID;

public record InternalBookingHistoryDetailsRequest(
        List<UUID> bookingIds
) {
}
