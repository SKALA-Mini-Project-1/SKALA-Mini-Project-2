package com.example.SKALA_Mini_Project_1.modules.bookings.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.SKALA_Mini_Project_1.modules.bookings.dto.InternalBookingHistoryDetailsRequest;
import com.example.SKALA_Mini_Project_1.modules.bookings.dto.InternalBookingHistoryDetailsResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.dto.InternalBookingPaymentContextResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.dto.InternalUserBookingIdsResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.service.InternalBookingQueryService;
import com.example.SKALA_Mini_Project_1.modules.finalization.service.InternalApiGuard;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/bookings")
public class InternalBookingQueryController {

    private final InternalApiGuard internalApiGuard;
    private final InternalBookingQueryService internalBookingQueryService;

    @GetMapping("/{bookingId}/payment-context")
    public ResponseEntity<InternalBookingPaymentContextResponse> getPaymentContext(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @PathVariable UUID bookingId
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(internalBookingQueryService.getPaymentContext(bookingId));
    }

    @GetMapping("/users/{userId}/ids")
    public ResponseEntity<InternalUserBookingIdsResponse> getUserBookingIds(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @PathVariable Long userId
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(internalBookingQueryService.getUserBookingIds(userId));
    }

    @PostMapping("/history-details")
    public ResponseEntity<InternalBookingHistoryDetailsResponse> getHistoryDetails(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @RequestBody InternalBookingHistoryDetailsRequest request
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(internalBookingQueryService.getHistoryDetails(request.bookingIds()));
    }
}
