package com.example.SKALA_Mini_Project_1.modules.payments.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentConfirmRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentConfirmResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCreateRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCreateResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentGetResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentSubmitResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.RefundRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.TossWebhookRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.service.PaymentService;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;


    @PostMapping("/create")
    public ResponseEntity<PaymentCreateResponse> create(@Valid @RequestBody PaymentCreateRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return ResponseEntity.ok(paymentService.createPayment(req.getBookingId(), userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentGetResponse> get(@PathVariable UUID id) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return ResponseEntity.ok(paymentService.getPayment(id, userId));
    }

    @PostMapping("/{paymentId}/submit")
    public PaymentSubmitResponse submit(
            @PathVariable UUID paymentId
    ) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return paymentService.submit(paymentId, userId);
    }

    @PostMapping("/confirm")
    public PaymentConfirmResponse confirm(@RequestBody PaymentConfirmRequest request) {
        return paymentService.confirm(request);
    }


    @GetMapping("/toss/success")
    public ResponseEntity<Void> tossSuccess(
        @RequestParam String paymentKey,
        @RequestParam String orderId,
        @RequestParam Long amount) {

    paymentService.handleTossSuccess(paymentKey, orderId, amount);

        URI redirect = URI.create(
            "http://localhost:5173/payments/success?paymentKey=" + paymentKey + "&orderId=" + orderId + "&amount=" + amount
        );

    return ResponseEntity.status(HttpStatus.FOUND)
            .location(redirect)
            .build();
    }

    @GetMapping("/toss/fail")
    public ResponseEntity<Void> tossFail(
        @RequestParam String orderId,
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String message) {

    paymentService.handleTossFail(orderId, code, message);

        URI redirect = URI.create(
            "http://localhost:5173/payments/fail?code=" + (code == null ? "PAYMENT_FAILED" : code)
                + "&message=" + (message == null ? "" : message)
                + "&orderId=" + orderId
        );

    return ResponseEntity.status(HttpStatus.FOUND)
            .location(redirect)
            .build();
    }  

    @PostMapping("/toss/webhook")
    public void tossWebhook(
            @RequestBody TossWebhookRequest request
    ) {
        paymentService.handleWebhook(request);
    }

    @GetMapping("/refunds/required")
    public ResponseEntity<List<Map<String, Object>>> refundRequired() {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return ResponseEntity.ok(paymentService.getRefundRequiredPayments(userId));
    }

    @PostMapping("/refunds/{paymentId}/request")
    public ResponseEntity<Map<String, Object>> requestRefund(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) RefundRequest request
    ) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        String reasonCode = request == null ? null : request.getReasonCode();
        return ResponseEntity.ok(paymentService.requestRefund(paymentId, userId, reasonCode));
    }

    @GetMapping("/ops/summary")
    public ResponseEntity<Map<String, Object>> opsSummary() {
        return ResponseEntity.ok(paymentService.getOpsSummary());
    }


}
