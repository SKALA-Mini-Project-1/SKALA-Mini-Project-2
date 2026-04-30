package com.example.SKALA_Mini_Project_1.modules.events.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.SKALA_Mini_Project_1.modules.events.dto.TicketingInboxEventItemResponse;
import com.example.SKALA_Mini_Project_1.modules.events.dto.TicketingInboxSummaryResponse;
import com.example.SKALA_Mini_Project_1.modules.events.service.TicketingInboxEventService;
import com.example.SKALA_Mini_Project_1.modules.finalization.service.InternalApiGuard;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/ops/inbox")
public class InternalInboxOpsController {

    private final InternalApiGuard internalApiGuard;
    private final TicketingInboxEventService ticketingInboxEventService;

    @GetMapping("/summary")
    public ResponseEntity<TicketingInboxSummaryResponse> getSummary(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(new TicketingInboxSummaryResponse(
                ticketingInboxEventService.countReceived(),
                ticketingInboxEventService.countDuplicate()
        ));
    }

    @GetMapping
    public ResponseEntity<List<TicketingInboxEventItemResponse>> getRecentEvents(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(ticketingInboxEventService.getRecentEvents().stream()
                .map(event -> new TicketingInboxEventItemResponse(
                        event.getId(),
                        event.getDedupeKey(),
                        event.getEventType(),
                        event.getProducer(),
                        event.getBookingId(),
                        event.getPaymentId(),
                        event.getAggregateId(),
                        event.getPgOrderId(),
                        event.getPgPaymentKey(),
                        event.getStatus(),
                        event.getDuplicateCount(),
                        event.getReceivedAt(),
                        event.getLastSeenAt()
                ))
                .toList());
    }
}
