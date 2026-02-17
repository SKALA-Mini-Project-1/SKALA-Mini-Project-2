// 결제 상태를 전이표에 맞게만 변경하도록 강제하는 서비스

package com.example.SKALA_Mini_Project_1.modules.payments.service;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.example.SKALA_Mini_Project_1.modules.bookings.domain.Booking;
import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import com.example.SKALA_Mini_Project_1.modules.payments.client.TossConfirmResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingItemRepository;
import com.example.SKALA_Mini_Project_1.modules.seats.repository.SeatRepository;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentConfirmRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentConfirmResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCreateResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentGetResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentSubmitResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.TossWebhookRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentEvent;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.Payment;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentStatus;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.Refund;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentEventRepository;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentRepository;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.RefundRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate; // ✅ Bean 주입
    private final com.example.SKALA_Mini_Project_1.modules.payments.client.TossPaymentsClient tossPaymentsClient;
    private final com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final SeatRepository seatRepository;
    private final RefundRepository refundRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final Map<PaymentStatus, Set<PaymentStatus>> transitionMap = new EnumMap<>(PaymentStatus.class);


        /**
     * 결제 생성: PENDING + expiredAt = now + 5분
     */
    // Create
    @Transactional
public PaymentCreateResponse createPayment(UUID bookingId, Long userId) {
    if (bookingId == null) {
        throw new IllegalArgumentException("bookingId is required");
    }
    if (userId == null) {
        throw new IllegalArgumentException("userId is required");
    }

    // (권장) 동시성/중복 생성 방지: booking 락 조회로 바꾸면 더 안전
    Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

    if (!userId.equals(booking.getUserId())) {
        throw new IllegalArgumentException("booking user mismatch");
    }

    // ✅ booking_id UNIQUE 대응: 이미 결제가 있으면 새로 만들지 말고 그대로 반환
    return paymentRepository.findByBookingId(bookingId)
            .map(existing -> new PaymentCreateResponse(
                    existing.getId(),
                    existing.getStatus(),
                    existing.getCreatedAt(),
                    existing.getExpiredAt()
            ))
            .orElseGet(() -> {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                if (booking.getTotalPrice() == null) {
                    throw new IllegalStateException("booking totalPrice is required");
                }

                long amount = booking.getTotalPrice().longValue();
                if (amount <= 0) {
                    throw new IllegalStateException("booking amount is zero");
                }

                Payment payment = new Payment();
                payment.setBookingId(bookingId);
                payment.setUserId(userId);

                // seatId는 이제 의미가 애매함(booking이 다좌석 가능)
                // 최소한으로 유지하려면 대표 1개만 넣되, null이면 안되면 예외 처리
                Long firstSeatId = bookingItemRepository.findFirstSeatIdByBookingId(bookingId);
                if (firstSeatId == null) {
                    throw new IllegalStateException("no seat found for booking");
                }
                payment.setSeatId(firstSeatId);

                payment.setAmount(amount);

                // DB ck_payments_status 허용값과 반드시 일치해야 함
                payment.setStatus(PaymentStatus.PENDING);

                payment.setCreatedAt(now);
                payment.setUpdatedAt(now);
                payment.setExpiredAt(now.plusMinutes(5));
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
    public PaymentGetResponse getPayment(UUID paymentId, Long userId) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        ensureOwner(p, userId);

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
    public PaymentSubmitResponse submit(UUID paymentId, Long userId) {

    Payment payment = paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
    ensureOwner(payment, userId);

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    // 상태 전이
    String fromStatus = payment.getStatus() == null ? null : payment.getStatus().name();
    payment.changeStatus(PaymentStatus.PAYING);
    recordEvent(payment, "SUBMIT_PAYING", fromStatus, payment.getStatus().name(), null, payment.getPgOrderId());

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
            payment.getStatus(),
            payment.getExpiredAt(),
            payment.getIdempotencyKey(),
            payment.getUpdatedAt(),
            payment.getBookingId(),
            payment.getAmount(),
            orderId,
            "USER_" + userId,
            payment.getOrderName(),
            "http://localhost:5173/payments/success",
            "http://localhost:5173/payments/fail"
    );
    }

    @Value("${toss.secret-key}")
    private String tossSecretKey;

    @Value("${toss.api-base}")
    private String tossApiBase;

    @Transactional
    public void handleTossSuccess(String paymentKey, String orderId, Long amount) {

        // 1) 결제 조회 (비관락)
        Payment payment = paymentRepository.findByPgOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + orderId));

        // 2) 금액 위변조 방지 (Long 기준)
        if (payment.getAmount() == null || amount == null || !payment.getAmount().equals(amount)) {
            throw new IllegalStateException("Amount mismatch");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // 3) 만료 여부 판단
        boolean isExpiredNow = payment.getExpiredAt() != null && payment.getExpiredAt().isBefore(now);
        boolean isExpiredStatus = payment.getStatus() == PaymentStatus.EXPIRED;
        boolean shouldMarkRefundRequired = isExpiredNow || isExpiredStatus;

        // 4) Toss Confirm API 호출
        String confirmResponseBody;

        try {
            confirmResponseBody = tossConfirm(paymentKey, orderId, amount);
        } catch (RestClientResponseException e) {
            // ✅ 실패해도 PG 정보 저장해서 추적 가능하게
            payment.setPgPaymentKey(paymentKey);
            payment.setPgStatus(e.getResponseBodyAsString());
            payment.setUpdatedAt(now);
            paymentRepository.save(payment);

            throw new IllegalStateException("Toss confirm failed: " + e.getRawStatusCode());
        } catch (Exception e) {
            payment.setPgPaymentKey(paymentKey);
            payment.setPgStatus("CONFIRM_EXCEPTION: " + e.getClass().getSimpleName());
            payment.setUpdatedAt(now);
            paymentRepository.save(payment);

            throw new IllegalStateException("Toss confirm failed");
        }

        // 5) 승인 성공: pg 식별자/응답 저장
        payment.setPgPaymentKey(paymentKey);
        payment.setPgStatus(confirmResponseBody);
        payment.setUpdatedAt(now);

        // 6) 정책 A + 상태 전이
        if (shouldMarkRefundRequired) {
            if (payment.getStatus() != PaymentStatus.EXPIRED) {
                String from = payment.getStatus().name();
                payment.changeStatus(PaymentStatus.EXPIRED);
                recordEvent(payment, "PAYMENT_EXPIRED", from, payment.getStatus().name(), null, orderId);
            }
            String from = payment.getStatus().name();
            payment.changeStatus(PaymentStatus.REFUND_REQUIRED);
            recordEvent(payment, "REFUND_REQUIRED_MARKED", from, payment.getStatus().name(), null, orderId);
        } else {
            String from = payment.getStatus().name();
            payment.changeStatus(PaymentStatus.PAID);
            recordEvent(payment, "PAYMENT_PAID", from, payment.getStatus().name(), null, orderId);
            payment.setCompletedAt(now);
            confirmPayment(payment, now);
        }

        paymentRepository.save(payment);
    }

    // ----------------------------------------------------------------------
    // Toss Confirm Call
    // ----------------------------------------------------------------------
    private String tossConfirm(String paymentKey, String orderId, Long amount) {

        String basicToken = Base64.getEncoder().encodeToString(
                (tossSecretKey + ":").getBytes(StandardCharsets.UTF_8)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + basicToken);

        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = restTemplate.exchange(
                tossApiBase + "/v1/payments/confirm",
                HttpMethod.POST,
                request,
                String.class
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new IllegalStateException("Toss confirm unexpected response");
        }

        return resp.getBody();
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
        String from = payment.getStatus().name();
        payment.changeStatus(PaymentStatus.FAILED);
        recordEvent(payment, "PAYMENT_FAILED", from, payment.getStatus().name(), failInfo, orderId);
    }

    paymentRepository.save(payment);
    }

    @Transactional
    public PaymentConfirmResponse confirm(PaymentConfirmRequest request) {

        Payment payment = paymentRepository.findByPgOrderIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        // 멱등 처리
        if (payment.getStatus() == PaymentStatus.CONFIRMED) {

            return new PaymentConfirmResponse(
                    payment.getId(),
                    payment.getBookingId(),
                    payment.getStatus().name()
            );
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            confirmPayment(payment, OffsetDateTime.now(ZoneOffset.UTC));
            return new PaymentConfirmResponse(
                    payment.getId(),
                    payment.getBookingId(),
                    payment.getStatus().name()
            );
        }

        if (payment.getStatus() != PaymentStatus.PAYING) {
            throw new IllegalStateException("Invalid payment status: " + payment.getStatus());
        }

        long dbAmount = payment.getAmount().longValue();
        if (dbAmount != request.getAmount()) {
            throw new IllegalStateException("Amount mismatch");
        }

        // 🔥 토스 승인 API 호출
        TossConfirmResponse tossResponse = tossPaymentsClient.confirm(
                request.getPaymentKey(),
                request.getOrderId(),
                request.getAmount()
        );

        payment.setPgPaymentKey(tossResponse.getPaymentKey());
        payment.setPgStatus(tossResponse.getStatus());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        payment.setCompletedAt(now);
        payment.setUpdatedAt(now);
        String from = payment.getStatus().name();
        payment.changeStatus(PaymentStatus.PAID);
        recordEvent(payment, "PAYMENT_PAID", from, payment.getStatus().name(), null, request.getOrderId());
        confirmPayment(payment, now);

        return new PaymentConfirmResponse(
                payment.getId(),
                payment.getBookingId(),
                payment.getStatus().name()
        );
    }

    @Transactional
    public void handleWebhook(TossWebhookRequest request) {

        Payment payment = paymentRepository.findByPgOrderIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        payment.setPgPaymentKey(request.getPaymentKey());
        payment.setPgStatus(request.getStatus());
        payment.setUpdatedAt(now);
        String webhookType = "WEBHOOK_" + String.valueOf(request.getStatus()).toUpperCase() + "_RECEIVED";
        String currentStatus = payment.getStatus() == null ? null : payment.getStatus().name();
        recordEvent(payment, webhookType, currentStatus, currentStatus, null, request.getOrderId());

        // 이미 확정된 건 무시 (멱등성)
        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            return;
        }

        if ("DONE".equalsIgnoreCase(request.getStatus())) {
            if (payment.getStatus() == PaymentStatus.EXPIRED) {
                String from = payment.getStatus().name();
                payment.changeStatus(PaymentStatus.REFUND_REQUIRED);
                recordEvent(payment, "REFUND_REQUIRED_MARKED", from, payment.getStatus().name(), null, request.getOrderId());
                return;
            }

            if (payment.getStatus() == PaymentStatus.PAYING) {
                String from = payment.getStatus().name();
                payment.changeStatus(PaymentStatus.PAID);
                recordEvent(payment, "PAYMENT_PAID", from, payment.getStatus().name(), null, request.getOrderId());
                payment.setCompletedAt(now);
            }

            if (payment.getStatus() == PaymentStatus.PAID) {
                confirmPayment(payment, now);
            }

        } else if ("CANCELED".equalsIgnoreCase(request.getStatus())
                || "FAILED".equalsIgnoreCase(request.getStatus())) {
            if (payment.getStatus() == PaymentStatus.PAYING
                    || payment.getStatus() == PaymentStatus.PENDING) {
                String from = payment.getStatus().name();
                payment.changeStatus(PaymentStatus.FAILED);
                recordEvent(payment, "PAYMENT_FAILED", from, payment.getStatus().name(), null, request.getOrderId());
            } else {
                return;
            }

            Booking booking = bookingRepository.findById(payment.getBookingId())
                    .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

            booking.setStatus("CANCELED");
            booking.setCanceledAt(now);
        }
    }

    private void confirmPayment(Payment payment, OffsetDateTime now) {
        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("Payment must be PAID before confirm: " + payment.getStatus());
        }

        String from = payment.getStatus().name();
        payment.changeStatus(PaymentStatus.CONFIRMED);
        recordEvent(payment, "PAYMENT_CONFIRMED", from, payment.getStatus().name(), null, payment.getPgOrderId());
        payment.setUpdatedAt(now);
        seatRepository.reserveSeatsByBookingId(payment.getBookingId());

        Booking booking = bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
            booking.setStatus("CONFIRMED");
            booking.setConfirmedAt(now);
        }

        Long scheduleId = booking.getScheduleId();
        if (scheduleId != null) {
            Long concertId = bookingRepository.findConcertIdByBookingId(payment.getBookingId())
                    .orElseThrow(() -> new IllegalStateException("Concert not found for booking: " + payment.getBookingId()));
            String activeKey = RedisKeyGenerator.seatActiveKey(concertId, scheduleId);
            Long active = redisTemplate.opsForValue().decrement(activeKey);
            if (active == null || active < 0) {
                redisTemplate.opsForValue().set(activeKey, "0");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRefundRequiredPayments(Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("인증 사용자 정보가 없습니다.");
        }
        List<Payment> payments = paymentRepository.findTop50ByUserIdAndStatusOrderByUpdatedAtDesc(
                userId,
                PaymentStatus.REFUND_REQUIRED
        );
        List<Map<String, Object>> rows = new ArrayList<>();

        for (Payment p : payments) {
            Map<String, Object> row = new HashMap<>();
            row.put("paymentId", p.getId());
            row.put("bookingId", p.getBookingId());
            row.put("amount", p.getAmount());
            row.put("status", p.getStatus().name());
            row.put("pgOrderId", p.getPgOrderId());
            row.put("updatedAt", p.getUpdatedAt());
            rows.add(row);
        }

        return rows;
    }

    @Transactional
    public Map<String, Object> requestRefund(UUID paymentId, Long userId, String reasonCode) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        ensureOwner(payment, userId);

        if (payment.getStatus() != PaymentStatus.REFUND_REQUIRED) {
            throw new IllegalStateException("Payment is not REFUND_REQUIRED: " + payment.getStatus());
        }

        Refund existing = refundRepository.findTopByPaymentIdOrderByCreatedAtDesc(paymentId).orElse(null);
        if (existing != null && ("REQUESTED".equalsIgnoreCase(existing.getStatus())
                || "COMPLETED".equalsIgnoreCase(existing.getStatus()))) {
            Map<String, Object> already = new HashMap<>();
            already.put("refundId", existing.getId());
            already.put("paymentId", existing.getPaymentId());
            already.put("status", existing.getStatus());
            already.put("amount", existing.getAmount());
            already.put("requestedAt", existing.getRequestedAt());
            return already;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Refund refund = new Refund();
        refund.setPaymentId(paymentId);
        refund.setStatus("REQUESTED");
        refund.setReasonCode((reasonCode == null || reasonCode.isBlank()) ? "EXPIRED_AFTER_PAY" : reasonCode);
        refund.setAmount(BigDecimal.valueOf(payment.getAmount() == null ? 0L : payment.getAmount()));
        refund.setRequestedAt(now);
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        Refund saved = refundRepository.save(refund);

        recordEvent(payment, "REFUND_REQUESTED", payment.getStatus().name(), payment.getStatus().name(), null, payment.getPgOrderId());

        Map<String, Object> response = new HashMap<>();
        response.put("refundId", saved.getId());
        response.put("paymentId", saved.getPaymentId());
        response.put("status", saved.getStatus());
        response.put("reasonCode", saved.getReasonCode());
        response.put("amount", saved.getAmount());
        response.put("requestedAt", saved.getRequestedAt());
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOpsSummary() {
        long total = paymentRepository.count();
        long expired = paymentRepository.countByStatus(PaymentStatus.EXPIRED);
        long refundRequired = paymentRepository.countByStatus(PaymentStatus.REFUND_REQUIRED);
        long webhookDoneReceived = paymentEventRepository.countByEventType("WEBHOOK_DONE_RECEIVED");
        long duplicateWebhookDone = paymentEventRepository.countDuplicateDoneWebhookEvents();

        double expiredRate = total == 0 ? 0.0 : (expired * 100.0) / total;
        double refundRequiredRate = total == 0 ? 0.0 : (refundRequired * 100.0) / total;

        Map<String, Object> result = new HashMap<>();
        result.put("totalPayments", total);
        result.put("expiredPayments", expired);
        result.put("expiredRatePercent", Math.round(expiredRate * 100.0) / 100.0);
        result.put("refundRequiredPayments", refundRequired);
        result.put("refundRequiredRatePercent", Math.round(refundRequiredRate * 100.0) / 100.0);
        result.put("webhookDoneReceived", webhookDoneReceived);
        result.put("duplicateWebhookDoneEstimated", duplicateWebhookDone);
        return result;
    }

    private void recordEvent(
            Payment payment,
            String eventType,
            String fromStatus,
            String toStatus,
            String payloadJson,
            String pgEventId
    ) {
        PaymentEvent ev = new PaymentEvent();
        ev.setPaymentId(payment.getId());
        ev.setEventType(eventType);
        ev.setFromStatus(fromStatus);
        ev.setToStatus(toStatus);
        ev.setIdempotencyKey(payment.getIdempotencyKey());
        ev.setPgEventId(pgEventId);
        ev.setPayloadJson(payloadJson);
        ev.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        paymentEventRepository.save(ev);
    }

    private void ensureOwner(Payment payment, Long userId) {
        if (userId == null || payment.getUserId() == null || !payment.getUserId().equals(userId)) {
            throw new AccessDeniedException("본인 결제만 조회/처리할 수 있습니다.");
        }
    }


}
