// 결제 상태를 전이표에 맞게만 변경하도록 강제하는 서비스

package com.example.SKALA_Mini_Project_1.modules.payments.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCreateRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCreateResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentGetResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentSubmitResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.Payment;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentStatus;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    private static final Map<PaymentStatus, Set<PaymentStatus>> transitionMap = new EnumMap<>(PaymentStatus.class);


        /**
     * 결제 생성: PENDING + expiredAt = now + 5분
     */
    // Create
    @Transactional
    public PaymentCreateResponse createPayment(PaymentCreateRequest req) {
        if (req.getBookingId() == null) {
            throw new IllegalArgumentException("bookingId is required");
        }

        // // Booking 관련이 없어서 검증은 일단 주석처리
        // // 운영 기준: booking 존재 검증
        // if (!bookingRepository.existsById(req.getBookingId())) {
        //     throw new EntityNotFoundException("Booking not found: " + req.getBookingId());
        // }

        // ✅ booking_id UNIQUE 대응: 이미 결제가 있으면 새로 만들지 말고 그대로 반환
        return paymentRepository.findByBookingId(req.getBookingId())
            .map(existing -> new PaymentCreateResponse(
                    existing.getId(),
                    existing.getStatus(),
                    existing.getCreatedAt(),
                    existing.getExpiredAt()
            ))
            .orElseGet(() -> {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                Payment payment = new Payment();
                payment.setBookingId(req.getBookingId());
                payment.setUserId(req.getUserId());
                payment.setSeatId(req.getSeatId());
                payment.setAmount(req.getAmount());

                payment.setStatus(PaymentStatus.PENDING);
                payment.setCreatedAt(now);
                payment.setUpdatedAt(now);
                payment.setExpiredAt(now.plusMinutes(5)); // pending 5분
                payment.setIdempotencyKey(null);

                Payment saved = paymentRepository.save(payment);

                return new PaymentCreateResponse(
                        saved.getId(),
                        saved.getStatus(),
                        saved.getCreatedAt(),
                        saved.getExpiredAt()
                );
            });
    }

    /**
     * 결제 단건 조회
     */
    // Get
    @Transactional(readOnly = true)
    public PaymentGetResponse getPayment(UUID paymentId) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));

        return new PaymentGetResponse(
                p.getId(),
                p.getBookingId(),
                p.getUserId(),
                p.getSeatId(),
                p.getAmount(),
                p.getStatus().name(),
                p.getCreatedAt(),
                p.getExpiredAt(),
                p.getUpdatedAt(),
                p.getIdempotencyKey()
        );
    }

    @Transactional
    public PaymentSubmitResponse submit(UUID paymentId, UUID userId) {

    Payment payment = paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    // 상태 전이
    payment.changeStatus(PaymentStatus.PAYING);

    // TTL 연장
    OffsetDateTime base = payment.getExpiredAt();
    OffsetDateTime effective = (base == null || base.isBefore(now)) ? now : base;
    payment.setExpiredAt(effective.plusMinutes(3));

    payment.setIdempotencyKey(UUID.randomUUID().toString());
    payment.setUpdatedAt(now);

    // PG 정보 세팅
    payment.setPgProvider("TOSS");

    String orderId = "PAY_" + payment.getId();
    payment.setPgOrderId(orderId);

    // 임시 orderName 처리
    if (payment.getOrderName() == null) {
        payment.setOrderName("Ticket Payment");
    }

    payment.setSubmittedAt(now);

    paymentRepository.save(payment);

    return new PaymentSubmitResponse(
            payment.getId(),
            payment.getStatus().name(),
            payment.getExpiredAt(),
            payment.getIdempotencyKey(),
            payment.getUpdatedAt(),
            payment.getBookingId(),
            payment.getAmount(),
            orderId,
            "USER_" + userId,
            payment.getOrderName(),
            "http://localhost:8081/payments/toss/success",
            "http://localhost:8081/payments/toss/fail"
    );
    }

    @Transactional
    public void handleTossSuccess(String paymentKey, String orderId, Long amount) {

    Payment payment = paymentRepository.findByPgOrderIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

    // 금액 위변조 방지
    if (!payment.getAmount().equals(amount)) {
        throw new IllegalStateException("Amount mismatch");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    // TODO: 여기서 실제 Toss Confirm API 호출 필요
    // RestTemplate 또는 WebClient 사용해서 /v1/payments/confirm 호출

    payment.setPgPaymentKey(paymentKey);
    payment.setPgStatus("CONFIRMED");

    // 정책 A 적용
    if (payment.getExpiredAt().isBefore(now)
            || payment.getStatus() == PaymentStatus.EXPIRED) {

        payment.changeStatus(PaymentStatus.REFUND_REQUIRED);

    } else {
        payment.changeStatus(PaymentStatus.PAID);
        payment.setCompletedAt(now);
    }

    paymentRepository.save(payment);
    }

    @Transactional
    public void handleTossFail(String orderId, String code, String message) {

    Payment payment = paymentRepository.findByPgOrderIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + orderId));

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    // 실패 정보는 최소한 로그로라도 남긴다(컬럼 없으면 pgStatus에 합쳐 저장)
    String failInfo = "FAILED" 
            + (code != null ? ("|" + code) : "")
            + (message != null ? ("|" + message) : "");
    payment.setPgStatus(failInfo);
    payment.setUpdatedAt(now);

    if (payment.getStatus() == PaymentStatus.PAYING) {
        payment.changeStatus(PaymentStatus.FAILED);
    }

    paymentRepository.save(payment);
    }


}