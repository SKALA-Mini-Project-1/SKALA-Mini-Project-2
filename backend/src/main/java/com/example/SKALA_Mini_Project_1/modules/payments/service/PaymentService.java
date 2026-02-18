// 결제 상태를 전이표에 맞게만 변경하도록 강제하는 서비스

package com.example.SKALA_Mini_Project_1.modules.payments.service;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.example.SKALA_Mini_Project_1.modules.bookings.domain.Booking;
import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import com.example.SKALA_Mini_Project_1.modules.payments.client.TossConfirmResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingItemRepository;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingRepository;
import com.example.SKALA_Mini_Project_1.modules.seats.repository.SeatRepository;
import com.example.SKALA_Mini_Project_1.global.redis.RedisLockRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate; // ✅ Bean 주입
    private final com.example.SKALA_Mini_Project_1.modules.payments.client.TossPaymentsClient tossPaymentsClient;
    private final com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final SeatRepository seatRepository;
    private final RefundRepository refundRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final RedisLockRepository redisLockRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final Map<PaymentStatus, Set<PaymentStatus>> transitionMap = new EnumMap<>(PaymentStatus.class);
    private static final int CREATE_EXPIRE_MINUTES = 5;
    private static final int PAYING_HARD_DEADLINE_MINUTES = 10;
    private static final String BOOKING_STATUS_CANCELED = "CANCELED";
    private static final String BOOKING_STATUS_CONFIRMED = "CONFIRMED";


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
                payment.setExpiredAt(now.plusMinutes(CREATE_EXPIRE_MINUTES));
                payment.setHardDeadlineAt(null);
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

    // 결제 진행(PAYING) 구간은 하드 데드라인 10분 정책을 적용한다.
    OffsetDateTime hardDeadline = now.plusMinutes(PAYING_HARD_DEADLINE_MINUTES);
    payment.setExpiredAt(hardDeadline);
    payment.setHardDeadlineAt(hardDeadline);

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
        boolean isExpiredNow = (payment.getExpiredAt() != null && payment.getExpiredAt().isBefore(now))
                || (payment.getHardDeadlineAt() != null && payment.getHardDeadlineAt().isBefore(now));
        boolean isExpiredStatus = payment.getStatus() == PaymentStatus.EXPIRED;
        boolean invalidHold = !isHoldValidForPayment(payment.getBookingId());
        boolean shouldMarkRefundRequired = isExpiredNow || isExpiredStatus || invalidHold;

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
            markRefundRequired(payment, now, orderId, invalidHold ? "INVALID_HOLD" : null);
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

        if (!isHoldValidForPayment(payment.getBookingId())) {
            markRefundRequired(payment, now, request.getOrderId(), "INVALID_HOLD");
            return new PaymentConfirmResponse(
                    payment.getId(),
                    payment.getBookingId(),
                    payment.getStatus().name()
            );
        }

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
        String statusUpper = String.valueOf(request.getStatus()).toUpperCase();
        String webhookType = "WEBHOOK_" + statusUpper + "_RECEIVED";
        String webhookEventId = (request.getPaymentKey() == null || request.getPaymentKey().isBlank())
                ? request.getOrderId()
                : statusUpper + "|" + request.getPaymentKey();

        if (webhookEventId != null
                && paymentEventRepository.existsByPaymentIdAndEventTypeAndPgEventId(
                        payment.getId(), webhookType, webhookEventId)) {
            return;
        }

        String currentStatus = payment.getStatus() == null ? null : payment.getStatus().name();
        recordEvent(payment, webhookType, currentStatus, currentStatus, null, webhookEventId);

        // 이미 확정된 건 무시 (멱등성)
        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            return;
        }

        if ("DONE".equalsIgnoreCase(request.getStatus())) {
            if (payment.getStatus() == PaymentStatus.EXPIRED) {
                markRefundRequired(payment, now, request.getOrderId(), "LATE_DONE_AFTER_EXPIRED");
                return;
            }

            if (payment.getStatus() == PaymentStatus.PAYING) {
                if (!isHoldValidForPayment(payment.getBookingId())) {
                    markRefundRequired(payment, now, request.getOrderId(), "INVALID_HOLD");
                    return;
                }
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
            cancelBookingAndReleaseResources(payment.getBookingId(), now, BOOKING_STATUS_CANCELED);
        }
    }

    @Transactional
    public PaymentConfirmResponse cancelByUser(UUID paymentId, Long userId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        ensureOwner(payment, userId);

        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.PAYING) {
            throw new IllegalStateException("User cancel is only allowed in PENDING/PAYING: " + payment.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String from = payment.getStatus().name();
        payment.changeStatus(PaymentStatus.CANCELED);
        payment.setUpdatedAt(now);
        payment.setPgStatus("USER_CANCELED");
        recordEvent(payment, "PAYMENT_CANCELED_BY_USER", from, payment.getStatus().name(), null, payment.getPgOrderId());

        cancelBookingAndReleaseResources(payment.getBookingId(), now, BOOKING_STATUS_CANCELED);

        return new PaymentConfirmResponse(payment.getId(), payment.getBookingId(), payment.getStatus().name());
    }

    private void cancelBookingAndReleaseResources(UUID bookingId, OffsetDateTime now, String bookingStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        booking.setStatus(bookingStatus);
        booking.setCanceledAt(now);

        seatRepository.releaseSeatHoldsByBookingId(bookingId);

        Long scheduleId = booking.getScheduleId();
        Long userId = booking.getUserId();
        if (scheduleId == null || userId == null) {
            return;
        }

        Long concertId = bookingRepository.findConcertIdByBookingId(bookingId).orElse(null);
        if (concertId == null) {
            return;
        }

        scheduleSeatCleanupAfterCommit(concertId, scheduleId, userId);
    }

    private void markRefundRequired(Payment payment, OffsetDateTime now, String orderId, String reason) {
        String reasonPayload = reason == null ? null : "{\"reason\":\"" + reason + "\"}";
        if (payment.getStatus() != PaymentStatus.EXPIRED) {
            String from = payment.getStatus().name();
            payment.changeStatus(PaymentStatus.EXPIRED);
            recordEvent(payment, "PAYMENT_EXPIRED", from, payment.getStatus().name(), reasonPayload, orderId);
        }
        String from = payment.getStatus().name();
        payment.changeStatus(PaymentStatus.REFUND_REQUIRED);
        recordEvent(payment, "REFUND_REQUIRED_MARKED", from, payment.getStatus().name(), reasonPayload, orderId);
        payment.setUpdatedAt(now);
    }

    private boolean isHoldValidForPayment(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return false;
        }
        if (BOOKING_STATUS_CANCELED.equalsIgnoreCase(booking.getStatus())) {
            return false;
        }

        Long scheduleId = booking.getScheduleId();
        Long userId = booking.getUserId();
        if (scheduleId == null || userId == null) {
            return false;
        }

        Long concertId = bookingRepository.findConcertIdByBookingId(bookingId).orElse(null);
        if (concertId == null) {
            return false;
        }

        List<Long> seatIds = bookingItemRepository.findSeatIdsByBookingId(bookingId);
        if (seatIds == null || seatIds.isEmpty()) {
            return false;
        }

        String expectedOwner = String.valueOf(userId);
        for (Long seatId : seatIds) {
            if (seatId == null) {
                return false;
            }
            String owner = redisLockRepository.getSeatOwner(concertId, scheduleId, seatId);
            Long ttl = redisLockRepository.getSeatLockTtlSeconds(concertId, scheduleId, seatId);
            if (!expectedOwner.equals(owner) || ttl == null || ttl <= 0) {
                return false;
            }
        }
        return true;
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
        if (!BOOKING_STATUS_CONFIRMED.equalsIgnoreCase(booking.getStatus())) {
            booking.setStatus(BOOKING_STATUS_CONFIRMED);
            booking.setConfirmedAt(now);
        }

        Long scheduleId = booking.getScheduleId();
        Long userId = booking.getUserId();
        if (scheduleId != null && userId != null) {
            Long concertId = bookingRepository.findConcertIdByBookingId(payment.getBookingId())
                    .orElseThrow(() -> new IllegalStateException("Concert not found for booking: " + payment.getBookingId()));

            // 결제 확정 핵심(DB)과 Redis 정리(후처리)를 분리해 Redis 장애가 결제 확정을 깨지 않도록 한다.
            scheduleSeatCleanupAfterCommit(concertId, scheduleId, userId);
        }
    }

    private void scheduleSeatCleanupAfterCommit(Long concertId, Long scheduleId, Long userId) {
        Runnable cleanupTask = () -> cleanupSeatResources(concertId, scheduleId, userId);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanupTask.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanupTask.run();
            }
        });
    }

    private void cleanupSeatResources(Long concertId, Long scheduleId, Long userId) {
        try {
            String userIdRaw = String.valueOf(userId);
            redisLockRepository.releaseUserHeldSeats(concertId, scheduleId, userIdRaw);

            String accessKey = RedisKeyGenerator.seatAccessKey(userId, concertId, scheduleId);
            String accessByScheduleKey = RedisKeyGenerator.seatAccessByScheduleKey(userId, scheduleId);
            boolean hadAccess = Boolean.TRUE.equals(redisTemplate.hasKey(accessKey))
                    || Boolean.TRUE.equals(redisTemplate.hasKey(accessByScheduleKey));

            redisTemplate.delete(accessKey);
            redisTemplate.delete(accessByScheduleKey);
            redisTemplate.opsForSet().remove(
                    RedisKeyGenerator.seatAccessIndexKey(concertId, scheduleId),
                    userIdRaw
            );

            if (hadAccess) {
                redisLockRepository.decrementSeatActiveFloorZero(concertId, scheduleId);
            }
        } catch (RuntimeException e) {
            log.warn(
                    "Deferred seat cleanup failed. concertId={}, scheduleId={}, userId={}, reason={}",
                    concertId,
                    scheduleId,
                    userId,
                    e.getMessage()
            );
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMyPaymentHistory(Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("인증 사용자 정보가 없습니다.");
        }

        List<Payment> payments = paymentRepository.findTop100ByUserIdOrderByUpdatedAtDesc(userId);
        List<Map<String, Object>> rows = new ArrayList<>();

        for (Payment payment : payments) {
            Map<String, Object> row = new HashMap<>();
            row.put("paymentId", payment.getId());
            row.put("bookingId", payment.getBookingId());
            row.put("paymentStatus", payment.getStatus().name());
            row.put("amount", payment.getAmount());
            row.put("orderName", payment.getOrderName());
            row.put("pgOrderId", payment.getPgOrderId());
            row.put("createdAt", payment.getCreatedAt());
            row.put("submittedAt", payment.getSubmittedAt());
            row.put("paidAt", payment.getCompletedAt());
            row.put("updatedAt", payment.getUpdatedAt());
            row.put("canRefund", payment.getStatus() == PaymentStatus.REFUND_REQUIRED);

            Booking booking = bookingRepository.findById(payment.getBookingId()).orElse(null);
            if (booking != null) {
                row.put("bookingStatus", booking.getStatus());
                row.put("bookingConfirmedAt", booking.getConfirmedAt());
                row.put("bookingCanceledAt", booking.getCanceledAt());
            } else {
                row.put("bookingStatus", null);
                row.put("bookingConfirmedAt", null);
                row.put("bookingCanceledAt", null);
            }

            BookingRepository.BookingConcertInfo concertInfo = bookingRepository.findBookingConcertInfo(payment.getBookingId())
                    .orElse(null);
            if (concertInfo != null) {
                row.put("concertName", concertInfo.getConcertTitle());
                row.put("concertVenue", concertInfo.getConcertVenue());
                Instant showTime = concertInfo.getShowTime();
                row.put(
                        "showDateTime",
                        showTime == null ? null : OffsetDateTime.ofInstant(showTime, ZoneOffset.UTC)
                );
            } else {
                row.put("concertName", null);
                row.put("concertVenue", null);
                row.put("showDateTime", null);
            }

            List<Object[]> seatRows = bookingItemRepository.findBookedSeatDetails(payment.getBookingId());
            List<Map<String, Object>> seats = new ArrayList<>();
            List<String> seatLabels = new ArrayList<>();
            for (Object[] s : seatRows) {
                String section = s[1] == null ? null : s[1].toString();
                Integer rowNumber = toInteger(s[2]);
                Integer seatNumber = toInteger(s[3]);
                Map<String, Object> seat = new HashMap<>();
                seat.put("seatId", toLong(s[0]));
                seat.put("section", section);
                seat.put("rowNumber", rowNumber);
                seat.put("seatNumber", seatNumber);
                seat.put("grade", s[4] == null ? null : s[4].toString());
                seat.put("price", toBigDecimal(s[5]));
                seats.add(seat);
                if (section != null && rowNumber != null && seatNumber != null) {
                    seatLabels.add(section + "-" + rowNumber + "-" + seatNumber);
                }
            }
            row.put("seatCount", seats.size());
            row.put("seatLabels", seatLabels);
            row.put("seats", seats);

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

    @Transactional
    public Map<String, Object> completeRefund(UUID paymentId, Long userId, String paymentStatus, String pgRefundId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        ensureOwner(payment, userId);

        if (payment.getStatus() != PaymentStatus.REFUND_REQUIRED) {
            throw new IllegalStateException("Payment is not REFUND_REQUIRED: " + payment.getStatus());
        }

        Refund refund = refundRepository.findTopByPaymentIdOrderByCreatedAtDesc(paymentId)
                .orElseThrow(() -> new IllegalStateException("No refund request found for payment: " + paymentId));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        refund.setStatus("COMPLETED");
        refund.setCompletedAt(now);
        refund.setUpdatedAt(now);
        if (pgRefundId != null && !pgRefundId.isBlank()) {
            refund.setPgRefundId(pgRefundId);
        }

        PaymentStatus target = "CANCELED".equalsIgnoreCase(paymentStatus)
                ? PaymentStatus.CANCELED
                : PaymentStatus.REFUNDED;
        String from = payment.getStatus().name();
        payment.changeStatus(target);
        payment.setUpdatedAt(now);
        recordEvent(payment, "REFUND_COMPLETED", from, payment.getStatus().name(), null, payment.getPgOrderId());

        Map<String, Object> response = new HashMap<>();
        response.put("paymentId", payment.getId());
        response.put("paymentStatus", payment.getStatus().name());
        response.put("refundId", refund.getId());
        response.put("refundStatus", refund.getStatus());
        response.put("completedAt", refund.getCompletedAt());
        response.put("pgRefundId", refund.getPgRefundId());
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

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).longValue();
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).intValue();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(value.toString());
    }


}
