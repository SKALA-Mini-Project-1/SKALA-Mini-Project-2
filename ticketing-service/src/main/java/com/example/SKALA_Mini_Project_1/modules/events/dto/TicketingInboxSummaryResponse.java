package com.example.SKALA_Mini_Project_1.modules.events.dto;

public record TicketingInboxSummaryResponse(
        long receivedCount,
        long duplicateCount
) {
}
