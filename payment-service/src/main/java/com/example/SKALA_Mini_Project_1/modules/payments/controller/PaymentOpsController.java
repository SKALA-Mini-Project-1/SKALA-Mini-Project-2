package com.example.SKALA_Mini_Project_1.modules.payments.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentOpsSummaryResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentRefundRequiredItemResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentSchedulerHealthResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentWebhookEventResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/ops")
@Tag(name = "결제 운영", description = "결제 운영용 내부 조회 API")
public class PaymentOpsController {

    private final PaymentService paymentService;
    private final PaymentOpsApiGuard paymentOpsApiGuard;

    @GetMapping("/summary")
    @Operation(summary = "운영 요약 조회", description = "결제 운영 지표 요약 정보를 조회합니다.")
    public ResponseEntity<PaymentOpsSummaryResponse> getSummary(
            @RequestHeader(PaymentOpsApiGuard.HEADER_NAME) String apiKey
    ) {
        paymentOpsApiGuard.validate(apiKey);
        return ResponseEntity.ok(paymentService.getOpsSummary());
    }

    @GetMapping("/refund-required")
    public ResponseEntity<List<PaymentRefundRequiredItemResponse>> getRefundRequiredPayments(
            @RequestHeader(PaymentOpsApiGuard.HEADER_NAME) String apiKey
    ) {
        paymentOpsApiGuard.validate(apiKey);
        return ResponseEntity.ok(paymentService.getOpsRefundRequiredPayments());
    }

    @GetMapping("/webhooks")
    public ResponseEntity<List<PaymentWebhookEventResponse>> getWebhookEvents(
            @RequestHeader(PaymentOpsApiGuard.HEADER_NAME) String apiKey
    ) {
        paymentOpsApiGuard.validate(apiKey);
        return ResponseEntity.ok(paymentService.getOpsWebhookEvents());
    }

    @GetMapping("/schedulers/health")
    public ResponseEntity<PaymentSchedulerHealthResponse> getSchedulerHealth(
            @RequestHeader(PaymentOpsApiGuard.HEADER_NAME) String apiKey
    ) {
        paymentOpsApiGuard.validate(apiKey);
        return ResponseEntity.ok(paymentService.getOpsSchedulerHealth());
    }
}
