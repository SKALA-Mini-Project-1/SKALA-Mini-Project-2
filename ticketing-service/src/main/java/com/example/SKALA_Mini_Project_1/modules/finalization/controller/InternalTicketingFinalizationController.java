package com.example.SKALA_Mini_Project_1.modules.finalization.controller;

import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingCancelRequest;
import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingConfirmRequest;
import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingExpireRequest;
import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingFinalizationResponse;
import com.example.SKALA_Mini_Project_1.modules.finalization.service.InternalApiGuard;
import com.example.SKALA_Mini_Project_1.modules.finalization.service.TicketingFinalizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/finalizations")
public class InternalTicketingFinalizationController {

    private final InternalApiGuard internalApiGuard;
    private final TicketingFinalizationService ticketingFinalizationService;

    @PostMapping("/confirm")
    public ResponseEntity<InternalBookingFinalizationResponse> confirmBooking(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @Valid @RequestBody InternalBookingConfirmRequest request
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(ticketingFinalizationService.confirmBooking(request));
    }

    @PostMapping("/cancel")
    public ResponseEntity<InternalBookingFinalizationResponse> cancelBooking(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @Valid @RequestBody InternalBookingCancelRequest request
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(ticketingFinalizationService.cancelBooking(request));
    }

    @PostMapping("/expire")
    public ResponseEntity<InternalBookingFinalizationResponse> expireBooking(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @Valid @RequestBody InternalBookingExpireRequest request
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(ticketingFinalizationService.expireBooking(request));
    }
}
