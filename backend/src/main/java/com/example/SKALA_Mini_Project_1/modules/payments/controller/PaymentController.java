package com.example.SKALA_Mini_Project_1.modules.payments.controller;

import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCreateRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCreateResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentGetResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentSubmitResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.service.PaymentService;


@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;


    @PostMapping("/create")
    public ResponseEntity<PaymentCreateResponse> create(@RequestBody PaymentCreateRequest req) {
        return ResponseEntity.ok(paymentService.createPayment(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentGetResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    // 인증 생기면 userId 헤더 대신 @AuthenticationPrincipal 사용
    @PostMapping("/{paymentId}/submit")
    public PaymentSubmitResponse submit(
            @PathVariable UUID paymentId,
            @RequestHeader("X-USER-ID") UUID userId
    ) {
        return paymentService.submit(paymentId, userId);
    }


    @GetMapping("/toss/success")
    public ResponseEntity<Void> tossSuccess(
        @RequestParam String paymentKey,
        @RequestParam String orderId,
        @RequestParam Long amount) {

    paymentService.handleTossSuccess(paymentKey, orderId, amount);

    URI redirect = URI.create(
            "http://localhost:3000/payments/result?orderId=" + orderId + "&result=success"
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
            "http://localhost:3000/payments/result?orderId=" + orderId + "&result=fail"
    );

    return ResponseEntity.status(HttpStatus.FOUND)
            .location(redirect)
            .build();
    }  


}
