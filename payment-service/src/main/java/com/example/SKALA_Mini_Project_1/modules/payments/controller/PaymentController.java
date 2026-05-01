package com.example.SKALA_Mini_Project_1.modules.payments.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentConfirmRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentConfirmResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCancelRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCreateRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCreateResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentExitSignalRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentGetResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentHistoryItemResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentRefundRequiredItemResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.RefundCompleteRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.RefundCompletionResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentSubmitResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.RefundRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.RefundStatusResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.TossWebhookRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.service.PaymentService;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
@Tag(name = "결제", description = "결제 생성/진행/확정/취소/환불 및 결제 내역 조회 API")
public class PaymentController {

    private final PaymentService paymentService;
    @Value("${payment.redirect.success-url:http://localhost:5173/payments/success}")
    private String paymentSuccessRedirectUrl;
    @Value("${payment.redirect.fail-url:http://localhost:5173/payments/fail}")
    private String paymentFailRedirectUrl;


    @PostMapping("/create")
    @Operation(summary = "결제 생성", description = "예약 정보를 기반으로 결제 레코드를 생성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결제 생성 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "본인 예약이 아님"),
            @ApiResponse(responseCode = "409", description = "결제 생성 불가 상태")
    })
    public ResponseEntity<PaymentCreateResponse> create(@Valid @RequestBody PaymentCreateRequest req) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return ResponseEntity.ok(paymentService.createPayment(req.getBookingId(), userId));
    }

    @GetMapping("/{id:[0-9a-fA-F-]{36}}")
    @Operation(summary = "결제 단건 조회", description = "결제 ID로 현재 결제 상태 및 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "본인 결제가 아님"),
            @ApiResponse(responseCode = "404", description = "결제 없음")
    })
    public ResponseEntity<PaymentGetResponse> get(@PathVariable UUID id) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return ResponseEntity.ok(paymentService.getPayment(id, userId));
    }

    @PostMapping("/{paymentId}/submit")
    @Operation(summary = "결제 제출", description = "결제창 진입 직전 결제를 PAYING 상태로 전이합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결제 제출 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "본인 결제가 아님"),
            @ApiResponse(responseCode = "409", description = "현재 상태에서 제출 불가")
    })
    public PaymentSubmitResponse submit(
            @PathVariable UUID paymentId
    ) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return paymentService.submit(paymentId, userId);
    }

    @PostMapping("/{paymentId}/cancel-booking")
    @Operation(summary = "예매 취소 (확정 후)", description = "결제 확정된 예매를 취소하고 PG 환불을 처리합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "예매 취소 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "본인 결제가 아님"),
            @ApiResponse(responseCode = "409", description = "현재 상태에서 취소 불가")
    })
    public ResponseEntity<PaymentConfirmResponse> cancelBooking(@PathVariable UUID paymentId) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return ResponseEntity.ok(paymentService.cancelConfirmedBooking(paymentId, userId));
    }

    @PostMapping("/{paymentId}/cancel")
    @Operation(summary = "사용자 결제 취소", description = "사용자가 진행 중 결제를 취소하고 관련 자원을 해제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "취소 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "본인 결제가 아님"),
            @ApiResponse(responseCode = "409", description = "현재 상태에서 취소 불가")
    })
    public PaymentConfirmResponse cancel(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) PaymentCancelRequest request
    ) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return paymentService.cancelByUser(paymentId, userId, request);
    }

    @PostMapping("/{paymentId}/exit-signal")
    @Operation(summary = "결제 화면 이탈 감지", description = "결제 화면 이탈 신호를 기록해 운영 추적에 활용합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "기록 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "본인 결제가 아님")
    })
    public ResponseEntity<Void> exitSignal(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) PaymentExitSignalRequest request
    ) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        paymentService.recordExitSignal(paymentId, userId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/confirm")
    @Operation(summary = "결제 승인 확정", description = "PG 성공 리다이렉트 후 결제 승인을 최종 확정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "승인 확정 성공"),
            @ApiResponse(responseCode = "400", description = "요청 값 오류"),
            @ApiResponse(responseCode = "409", description = "상태 불일치 또는 중복 처리")
    })
    public PaymentConfirmResponse confirm(@RequestBody PaymentConfirmRequest request) {
        return paymentService.confirm(request);
    }


    @GetMapping("/toss/success")
    @Operation(summary = "토스 성공 콜백", description = "토스 성공 파라미터를 수신하고 프론트 성공 페이지로 리다이렉트합니다.")
    public ResponseEntity<Void> tossSuccess(
        @RequestParam String paymentKey,
        @RequestParam String orderId,
        @RequestParam Long amount) {

    paymentService.handleTossSuccess(paymentKey, orderId, amount);

        URI redirect = URI.create(
            paymentSuccessRedirectUrl + "?paymentKey=" + paymentKey + "&orderId=" + orderId + "&amount=" + amount
        );

    return ResponseEntity.status(HttpStatus.FOUND)
            .location(redirect)
            .build();
    }

    @GetMapping("/toss/fail")
    @Operation(summary = "토스 실패 콜백", description = "토스 실패 파라미터를 수신하고 프론트 실패 페이지로 리다이렉트합니다.")
    public ResponseEntity<Void> tossFail(
        @RequestParam String orderId,
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String message) {

    paymentService.handleTossFail(orderId, code, message);

        URI redirect = URI.create(
            paymentFailRedirectUrl + "?code=" + (code == null ? "PAYMENT_FAILED" : code)
                + "&message=" + (message == null ? "" : message)
                + "&orderId=" + orderId
        );

    return ResponseEntity.status(HttpStatus.FOUND)
            .location(redirect)
            .build();
    }  

    @PostMapping("/toss/webhook")
    @Operation(summary = "토스 웹훅 수신", description = "PG 비동기 이벤트를 수신해 결제 상태를 동기화합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "웹훅 처리 성공")
    })
    public void tossWebhook(
            @RequestBody TossWebhookRequest request
    ) {
        paymentService.handleWebhook(request);
    }

    @GetMapping("/refunds/required")
    @Operation(summary = "환불 필요 결제 목록 조회", description = "현재 사용자 기준 REFUND_REQUIRED 상태 결제 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<List<PaymentRefundRequiredItemResponse>> refundRequired() {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return ResponseEntity.ok(paymentService.getRefundRequiredPayments(userId));
    }

    @GetMapping("/history")
    @Operation(summary = "내 결제 내역 조회", description = "현재 로그인한 사용자의 결제 내역을 최신순으로 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<List<PaymentHistoryItemResponse>> history() {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return ResponseEntity.ok(paymentService.getMyPaymentHistory(userId));
    }

    @PostMapping("/refunds/{paymentId}/request")
    @Operation(summary = "환불 요청 등록", description = "결제에 대한 환불 요청 사유를 등록합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "요청 등록 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "본인 결제가 아님"),
            @ApiResponse(responseCode = "409", description = "환불 요청 불가 상태")
    })
    public ResponseEntity<RefundStatusResponse> requestRefund(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) RefundRequest request
    ) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        String reasonCode = request == null ? null : request.getReasonCode();
        return ResponseEntity.ok(paymentService.requestRefund(paymentId, userId, reasonCode));
    }

    @PostMapping("/refunds/{paymentId}/complete")
    @Operation(summary = "환불 완료 처리", description = "환불 완료를 반영해 최종 결제 상태를 갱신합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "환불 완료 처리 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "본인 결제가 아님"),
            @ApiResponse(responseCode = "409", description = "환불 완료 처리 불가 상태")
    })
    public ResponseEntity<RefundCompletionResponse> completeRefund(
            @PathVariable UUID paymentId,
            @RequestBody(required = false) RefundCompleteRequest request
    ) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        String paymentStatus = request == null ? null : request.getPaymentStatus();
        String pgRefundId = request == null ? null : request.getPgRefundId();
        return ResponseEntity.ok(paymentService.completeRefund(paymentId, userId, paymentStatus, pgRefundId));
    }

}
