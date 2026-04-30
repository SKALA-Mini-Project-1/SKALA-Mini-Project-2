// 결제 상태를 전이표에 맞게만 변경하도록 강제하는 서비스

package com.example.SKALA_Mini_Project_1.modules.payments.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing.InternalBookedSeatDetailResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing.InternalBookingHistoryDetailResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing.InternalBookingHistoryDetailsResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing.InternalBookingPaymentContextResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing.InternalUserBookingIdsResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.integration.ticketing.TicketingBookingQueryClient;
import com.example.SKALA_Mini_Project_1.modules.payments.integration.toss.TossConfirmResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.integration.toss.TossPaymentsClient;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCancelRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentConfirmRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentConfirmResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentCreateResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentExitSignalRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentGetResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentHistoryItemResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentHistorySeatResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentOpsSummaryResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentRefundRequiredItemResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentSchedulerHealthResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentSubmitResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.PaymentWebhookEventResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.RefundCompletionResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.RefundStatusResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.controller.dto.TossWebhookRequest;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.Payment;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentStatus;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.Refund;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentEventRepository;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentRepository;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.RefundRepository;
import org.springframework.security.access.AccessDeniedException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final RefundRepository refundRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaymentEventRecorder paymentEventRecorder;
    private final TicketingBookingQueryClient ticketingBookingQueryClient;
    @Value("${payment.redirect.success-url:http://localhost:5173/payments/success}")
    private String paymentSuccessRedirectUrl;
    @Value("${payment.redirect.fail-url:http://localhost:5173/payments/fail}")
    private String paymentFailRedirectUrl;
    @Value("${payment.expiration.create-minutes:5}")
    private long createExpireMinutes;
    @Value("${payment.expiration.paying-hard-deadline-minutes:10}")
    private long payingHardDeadlineMinutes;
    @Value("${payment.scheduler.expire.fixed-delay-ms:15000}")
    private long paymentExpireSchedulerDelayMs;


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

    InternalBookingPaymentContextResponse bookingContext = ticketingBookingQueryClient.getPaymentContext(bookingId);

    if (!userId.equals(bookingContext.userId())) {
        throw new IllegalArgumentException("booking user mismatch");
    }

    return paymentRepository.findByBookingId(bookingId)
            .map(existing -> new PaymentCreateResponse(
                    existing.getId(),
                    existing.getStatus(),
                    existing.getCreatedAt(),
                    existing.getExpiredAt()
            ))
            .orElseGet(() -> {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

                if (bookingContext.totalPrice() == null) {
                    throw new IllegalStateException("booking totalPrice is required");
                }

                long amount = bookingContext.totalPrice().longValue();
                if (amount <= 0) {
                    throw new IllegalStateException("booking amount is zero");
                }

                Payment payment = new Payment();
                payment.setBookingId(bookingId);
                payment.setAmount(amount);
                payment.setStatus(PaymentStatus.PENDING);
                payment.setCreatedAt(now);
                payment.setUpdatedAt(now);
                payment.setExpiredAt(now.plusMinutes(createExpireMinutes));
                payment.setHardDeadlineAt(null);
                payment.setIdempotencyKey(null);

                Payment saved = paymentRepository.save(payment);
                paymentEventRecorder.record(
                        saved,
                        "PAYMENT_CREATED",
                        null,
                        saved.getStatus().name(),
                        null,
                        null
                );

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
    @Transactional(readOnly = true)
    public PaymentGetResponse getPayment(UUID paymentId, Long userId) {
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        ensureOwner(p, userId);

        return new PaymentGetResponse(
                p.getId(),
                p.getBookingId(),
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

    String fromStatus = payment.getStatus() == null ? null : payment.getStatus().name();
    payment.changeStatus(PaymentStatus.PAYING);
    paymentEventRecorder.record(payment, "SUBMIT_PAYING", fromStatus, payment.getStatus().name(), null, payment.getPgOrderId());

    OffsetDateTime hardDeadline = now.plusMinutes(payingHardDeadlineMinutes);
    payment.setExpiredAt(hardDeadline);
    payment.setHardDeadlineAt(hardDeadline);

    payment.setIdempotencyKey(UUID.randomUUID().toString());
    payment.setUpdatedAt(now);

    payment.setPgProvider("TOSS");

    String orderId = "PAY_" + payment.getId();
    payment.setPgOrderId(orderId);

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
            paymentSuccessRedirectUrl,
            paymentFailRedirectUrl
    );
    }

    @Transactional
    public void handleTossSuccess(String paymentKey, String orderId, Long amount) {

        Payment payment = paymentRepository.findByPgOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + orderId));

        if (payment.getAmount() == null || amount == null || !payment.getAmount().equals(amount)) {
            throw new IllegalStateException("Amount mismatch");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        boolean isExpiredNow = (payment.getExpiredAt() != null && payment.getExpiredAt().isBefore(now))
                || (payment.getHardDeadlineAt() != null && payment.getHardDeadlineAt().isBefore(now));
        boolean isExpiredStatus = payment.getStatus() == PaymentStatus.EXPIRED;
        boolean shouldMarkRefundRequired = isExpiredNow || isExpiredStatus;

        try {
            TossConfirmResponse tossResponse = tossPaymentsClient.confirm(paymentKey, orderId, amount);
            payment.setPgPaymentKey(tossResponse.getPaymentKey());
            payment.setPgStatus(tossResponse.getStatus());
        } catch (com.example.SKALA_Mini_Project_1.modules.payments.exception.TossPaymentsException e) {
            payment.setPgPaymentKey(paymentKey);
            payment.setPgStatus(e.getMessage());
            payment.setUpdatedAt(now);
            paymentRepository.save(payment);
            throw new IllegalStateException("Toss confirm failed", e);
        } catch (Exception e) {
            payment.setPgPaymentKey(paymentKey);
            payment.setPgStatus("CONFIRM_EXCEPTION: " + e.getClass().getSimpleName());
            payment.setUpdatedAt(now);
            paymentRepository.save(payment);
            throw new IllegalStateException("Toss confirm failed");
        }

        payment.setUpdatedAt(now);

        if (shouldMarkRefundRequired) {
            markRefundRequired(payment, now, orderId, isExpiredStatus ? "ALREADY_EXPIRED" : "EXPIRED_BEFORE_CONFIRM");
        } else {
            String from = payment.getStatus().name();
            payment.changeStatus(PaymentStatus.PAID);
            paymentEventRecorder.record(payment, "PAYMENT_PAID", from, payment.getStatus().name(), null, orderId);
            payment.setCompletedAt(now);
            confirmPayment(payment, now);
        }

        paymentRepository.save(payment);
    }

    @Transactional
    public void handleTossFail(String orderId, String code, String message) {

    Payment payment = paymentRepository.findByPgOrderIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + orderId));

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    String failInfo = "FAILED"
            + (code != null ? ("|" + code) : "")
            + (message != null ? ("|" + message) : "");
    payment.setPgStatus(failInfo);
    payment.setUpdatedAt(now);

    if (payment.getStatus() == PaymentStatus.PAYING) {
        String from = payment.getStatus().name();
        payment.changeStatus(PaymentStatus.FAILED);
        paymentEventRecorder.record(payment, "PAYMENT_FAILED", from, payment.getStatus().name(), failInfo, orderId);
    }

    paymentRepository.save(payment);
    }

    @Transactional
    public PaymentConfirmResponse confirm(PaymentConfirmRequest request) {

        Payment payment = paymentRepository.findByPgOrderIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

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
        paymentEventRecorder.record(payment, "PAYMENT_PAID", from, payment.getStatus().name(), null, request.getOrderId());
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
        paymentEventRecorder.record(payment, webhookType, currentStatus, currentStatus, null, webhookEventId);

        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            return;
        }

        if ("DONE".equalsIgnoreCase(request.getStatus())) {
            if (payment.getStatus() == PaymentStatus.EXPIRED) {
                markRefundRequired(payment, now, request.getOrderId(), "LATE_DONE_AFTER_EXPIRED");
                return;
            }

            if (payment.getStatus() == PaymentStatus.PAYING) {
                String from = payment.getStatus().name();
                payment.changeStatus(PaymentStatus.PAID);
                paymentEventRecorder.record(payment, "PAYMENT_PAID", from, payment.getStatus().name(), null, request.getOrderId());
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
                paymentEventRecorder.record(payment, "PAYMENT_FAILED", from, payment.getStatus().name(), request.getStatus(), request.getOrderId());
                // ticketing-service Kafka consumer가 비동기로 booking을 취소한다
            }
        }
    }

    @Transactional
    public PaymentConfirmResponse cancelByUser(UUID paymentId, Long userId, PaymentCancelRequest request) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        ensureOwner(payment, userId);

        if (payment.getStatus() == PaymentStatus.CANCELED) {
            return new PaymentConfirmResponse(payment.getId(), payment.getBookingId(), payment.getStatus().name());
        }

        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.PAYING) {
            throw new IllegalStateException("User cancel is only allowed in PENDING/PAYING: " + payment.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String from = payment.getStatus().name();
        payment.changeStatus(PaymentStatus.CANCELED);
        payment.setUpdatedAt(now);
        payment.setPgStatus("USER_CANCELED");
        paymentEventRecorder.recordWithMetadata(
                payment,
                "PAYMENT_CANCELED_BY_USER",
                from,
                payment.getStatus().name(),
                buildReasonMetadata(
                        request == null ? null : request.getReasonCode(),
                        request == null ? null : request.getSource(),
                        request == null ? null : request.getClientRoute(),
                        false
                ),
                payment.getPgOrderId()
        );
        // ticketing-service Kafka consumer가 비동기로 booking을 취소한다

        return new PaymentConfirmResponse(payment.getId(), payment.getBookingId(), payment.getStatus().name());
    }

    @Transactional
    public PaymentConfirmResponse cancelConfirmedBooking(UUID paymentId, Long userId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        ensureOwner(payment, userId);

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return new PaymentConfirmResponse(payment.getId(), payment.getBookingId(), payment.getStatus().name());
        }

        if (payment.getStatus() != PaymentStatus.CONFIRMED) {
            throw new IllegalStateException("Booking cancel is only allowed in CONFIRMED status: " + payment.getStatus());
        }

        tossPaymentsClient.cancel(
                payment.getPgPaymentKey(),
                payment.getAmount(),
                "사용자 예매 취소"
        );

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Refund refund = new Refund();
        refund.setPaymentId(paymentId);
        refund.setStatus("COMPLETED");
        refund.setReasonCode("USER_BOOKING_CANCEL");
        refund.setAmount(BigDecimal.valueOf(payment.getAmount() == null ? 0L : payment.getAmount()));
        refund.setRequestedAt(now);
        refund.setCompletedAt(now);
        refundRepository.save(refund);

        String from = payment.getStatus().name();
        payment.changeStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(now);
        paymentEventRecorder.recordWithMetadata(
                payment,
                "PAYMENT_CANCELED_BY_USER",
                from,
                payment.getStatus().name(),
                buildReasonMetadata("USER_BOOKING_CANCEL", "mypage", "/mypage", false),
                payment.getPgOrderId()
        );

        return new PaymentConfirmResponse(payment.getId(), payment.getBookingId(), payment.getStatus().name());
    }

    @Transactional
    public void recordExitSignal(UUID paymentId, Long userId, PaymentExitSignalRequest request) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        ensureOwner(payment, userId);

        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.PAYING) {
            return;
        }

        paymentEventRecorder.recordWithMetadata(
                payment,
                "PAYMENT_EXIT_DETECTED",
                payment.getStatus().name(),
                payment.getStatus().name(),
                buildReasonMetadata(
                        request == null ? null : request.getReasonCode(),
                        request == null ? null : request.getSource(),
                        request == null ? null : request.getClientRoute(),
                        true
                ),
                payment.getPgOrderId()
        );
    }

    private void markRefundRequired(Payment payment, OffsetDateTime now, String orderId, String reason) {
        String reasonInfo = reason == null ? null : reason;
        if (payment.getStatus() != PaymentStatus.EXPIRED) {
            String from = payment.getStatus().name();
            payment.changeStatus(PaymentStatus.EXPIRED);
            paymentEventRecorder.record(payment, "PAYMENT_EXPIRED", from, payment.getStatus().name(), reasonInfo, orderId);
        }
        String from = payment.getStatus().name();
        payment.changeStatus(PaymentStatus.REFUND_REQUIRED);
        paymentEventRecorder.record(payment, "REFUND_REQUIRED_MARKED", from, payment.getStatus().name(), reasonInfo, orderId);
        payment.setUpdatedAt(now);
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
        paymentEventRecorder.record(payment, "PAYMENT_CONFIRMED", from, payment.getStatus().name(), null, payment.getPgOrderId());
        payment.setUpdatedAt(now);
        // ticketing-service Kafka consumer가 PAYMENT_CONFIRMED 이벤트를 소비해 booking finalization을 수행한다
    }

    @Transactional(readOnly = true)
    public List<PaymentRefundRequiredItemResponse> getRefundRequiredPayments(Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("인증 사용자 정보가 없습니다.");
        }
        List<UUID> ownedBookingIds = getOwnedBookingIds(userId);
        if (ownedBookingIds.isEmpty()) {
            return List.of();
        }

        List<Payment> payments = paymentRepository.findTop50ByBookingIdInAndStatusOrderByUpdatedAtDesc(
                ownedBookingIds,
                PaymentStatus.REFUND_REQUIRED
        );
        return payments.stream()
                .map(p -> new PaymentRefundRequiredItemResponse(
                        p.getId(),
                        p.getBookingId(),
                        p.getAmount(),
                        p.getStatus().name(),
                        p.getPgOrderId(),
                        p.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryItemResponse> getMyPaymentHistory(Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("인증 사용자 정보가 없습니다.");
        }

        List<UUID> ownedBookingIds = getOwnedBookingIds(userId);
        if (ownedBookingIds.isEmpty()) {
            return List.of();
        }

        List<Payment> payments = paymentRepository.findTop100ByBookingIdInOrderByUpdatedAtDesc(ownedBookingIds);
        List<UUID> bookingIds = payments.stream()
                .map(Payment::getBookingId)
                .toList();
        InternalBookingHistoryDetailsResponse historyDetailsResponse =
                ticketingBookingQueryClient.getHistoryDetails(bookingIds);
        Map<UUID, InternalBookingHistoryDetailResponse> historyDetailsByBookingId = new HashMap<>();
        if (historyDetailsResponse != null && historyDetailsResponse.items() != null) {
            for (InternalBookingHistoryDetailResponse detail : historyDetailsResponse.items()) {
                historyDetailsByBookingId.put(detail.bookingId(), detail);
            }
        }

        List<PaymentHistoryItemResponse> rows = new ArrayList<>();

        for (Payment payment : payments) {
            InternalBookingHistoryDetailResponse detail = historyDetailsByBookingId.get(payment.getBookingId());
            List<PaymentHistorySeatResponse> seats = new ArrayList<>();
            List<String> seatLabels = detail == null || detail.seatLabels() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(detail.seatLabels());
            if (detail != null && detail.seats() != null) {
                for (InternalBookedSeatDetailResponse seatDetail : detail.seats()) {
                    seats.add(new PaymentHistorySeatResponse(
                            seatDetail.seatId(),
                            seatDetail.section(),
                            seatDetail.rowNumber(),
                            seatDetail.seatNumber(),
                            seatDetail.grade(),
                            seatDetail.price()
                    ));
                }
            }
            rows.add(new PaymentHistoryItemResponse(
                    payment.getId(),
                    payment.getBookingId(),
                    payment.getStatus().name(),
                    payment.getAmount(),
                    payment.getOrderName(),
                    payment.getPgOrderId(),
                    payment.getCreatedAt(),
                    payment.getSubmittedAt(),
                    payment.getCompletedAt(),
                    payment.getUpdatedAt(),
                    payment.getStatus() == PaymentStatus.REFUND_REQUIRED,
                    detail == null ? null : detail.bookingStatus(),
                    detail == null ? null : detail.bookingConfirmedAt(),
                    detail == null ? null : detail.bookingCanceledAt(),
                    detail == null ? null : detail.concertName(),
                    detail == null ? null : detail.concertVenue(),
                    detail == null ? null : detail.showDateTime(),
                    detail == null || detail.seatCount() == null ? seats.size() : detail.seatCount(),
                    seatLabels,
                    seats
            ));
        }

        return rows;
    }

    @Transactional
    public RefundStatusResponse requestRefund(UUID paymentId, Long userId, String reasonCode) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        ensureOwner(payment, userId);

        if (payment.getStatus() != PaymentStatus.REFUND_REQUIRED) {
            throw new IllegalStateException("Payment is not REFUND_REQUIRED: " + payment.getStatus());
        }

        Refund existing = refundRepository.findTopByPaymentIdOrderByRequestedAtDesc(paymentId).orElse(null);
        if (existing != null && ("REQUESTED".equalsIgnoreCase(existing.getStatus())
                || "COMPLETED".equalsIgnoreCase(existing.getStatus()))) {
            return new RefundStatusResponse(
                    existing.getId(),
                    existing.getPaymentId(),
                    existing.getStatus(),
                    existing.getReasonCode(),
                    existing.getAmount(),
                    existing.getRequestedAt(),
                    existing.getCompletedAt(),
                    existing.getPgRefundId()
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Refund refund = new Refund();
        refund.setPaymentId(paymentId);
        refund.setStatus("REQUESTED");
        refund.setReasonCode((reasonCode == null || reasonCode.isBlank()) ? "EXPIRED_AFTER_PAY" : reasonCode);
        refund.setAmount(BigDecimal.valueOf(payment.getAmount() == null ? 0L : payment.getAmount()));
        refund.setRequestedAt(now);
        Refund saved = refundRepository.save(refund);

        paymentEventRecorder.record(payment, "REFUND_REQUESTED", payment.getStatus().name(), payment.getStatus().name(), null, payment.getPgOrderId());

        return new RefundStatusResponse(
                saved.getId(),
                saved.getPaymentId(),
                saved.getStatus(),
                saved.getReasonCode(),
                saved.getAmount(),
                saved.getRequestedAt(),
                saved.getCompletedAt(),
                saved.getPgRefundId()
        );
    }

    @Transactional
    public RefundCompletionResponse completeRefund(UUID paymentId, Long userId, String paymentStatus, String pgRefundId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + paymentId));
        ensureOwner(payment, userId);

        if (payment.getStatus() != PaymentStatus.REFUND_REQUIRED) {
            throw new IllegalStateException("Payment is not REFUND_REQUIRED: " + payment.getStatus());
        }

        Refund refund = refundRepository.findTopByPaymentIdOrderByRequestedAtDesc(paymentId)
                .orElseThrow(() -> new IllegalStateException("No refund request found for payment: " + paymentId));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        refund.setStatus("COMPLETED");
        refund.setCompletedAt(now);
        if (pgRefundId != null && !pgRefundId.isBlank()) {
            refund.setPgRefundId(pgRefundId);
        }

        PaymentStatus target = "CANCELED".equalsIgnoreCase(paymentStatus)
                ? PaymentStatus.CANCELED
                : PaymentStatus.REFUNDED;
        String from = payment.getStatus().name();
        payment.changeStatus(target);
        payment.setUpdatedAt(now);
        paymentEventRecorder.record(payment, "REFUND_COMPLETED", from, payment.getStatus().name(), null, payment.getPgOrderId());

        return new RefundCompletionResponse(
                payment.getId(),
                payment.getStatus().name(),
                refund.getId(),
                refund.getStatus(),
                refund.getCompletedAt(),
                refund.getPgRefundId()
        );
    }

    @Transactional(readOnly = true)
    public PaymentOpsSummaryResponse getOpsSummary() {
        long total = paymentRepository.count();
        long expired = paymentRepository.countByStatus(PaymentStatus.EXPIRED);
        long refundRequired = paymentRepository.countByStatus(PaymentStatus.REFUND_REQUIRED);
        long webhookDoneReceived = paymentEventRepository.countByEventType("WEBHOOK_DONE_RECEIVED");
        long duplicateWebhookDone = paymentEventRepository.countDuplicateDoneWebhookEvents();

        double expiredRate = total == 0 ? 0.0 : (expired * 100.0) / total;
        double refundRequiredRate = total == 0 ? 0.0 : (refundRequired * 100.0) / total;

        return new PaymentOpsSummaryResponse(
                total,
                expired,
                Math.round(expiredRate * 100.0) / 100.0,
                refundRequired,
                Math.round(refundRequiredRate * 100.0) / 100.0,
                webhookDoneReceived,
                duplicateWebhookDone
        );
    }

    @Transactional(readOnly = true)
    public List<PaymentRefundRequiredItemResponse> getOpsRefundRequiredPayments() {
        return paymentRepository.findTop50ByStatusOrderByUpdatedAtDesc(PaymentStatus.REFUND_REQUIRED)
                .stream()
                .map(payment -> new PaymentRefundRequiredItemResponse(
                        payment.getId(),
                        payment.getBookingId(),
                        payment.getAmount(),
                        payment.getStatus().name(),
                        payment.getPgOrderId(),
                        payment.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentWebhookEventResponse> getOpsWebhookEvents() {
        return paymentEventRepository.findTop100ByEventTypeStartingWithOrderByCreatedAtDesc("WEBHOOK_")
                .stream()
                .map(event -> new PaymentWebhookEventResponse(
                        event.getEventId(),
                        event.getPaymentId(),
                        event.getEventType(),
                        event.getPgEventId(),
                        event.getFromStatus(),
                        event.getToStatus(),
                        event.getCreatedAt(),
                        event.getOccurredAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentSchedulerHealthResponse getOpsSchedulerHealth() {
        return new PaymentSchedulerHealthResponse(
                paymentExpireSchedulerDelayMs,
                paymentRepository.countByStatus(PaymentStatus.PENDING),
                paymentRepository.countByStatus(PaymentStatus.PAYING),
                paymentRepository.countByStatus(PaymentStatus.EXPIRED),
                paymentRepository.countByStatus(PaymentStatus.REFUND_REQUIRED)
        );
    }

    private void ensureOwner(Payment payment, Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("본인 결제만 조회/처리할 수 있습니다.");
        }
        InternalBookingPaymentContextResponse bookingContext =
                ticketingBookingQueryClient.getPaymentContext(payment.getBookingId());
        if (bookingContext.userId() == null || !bookingContext.userId().equals(userId)) {
            throw new AccessDeniedException("본인 결제만 조회/처리할 수 있습니다.");
        }
    }

    private List<UUID> getOwnedBookingIds(Long userId) {
        InternalUserBookingIdsResponse response = ticketingBookingQueryClient.getUserBookingIds(userId);
        if (response == null || response.bookingIds() == null) {
            return List.of();
        }
        return response.bookingIds();
    }

    private Map<String, Object> buildReasonMetadata(String reasonCode, String source, String clientRoute, boolean bestEffort) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (reasonCode != null && !reasonCode.isBlank()) {
            metadata.put("reason", reasonCode);
        }
        if (source != null && !source.isBlank()) {
            metadata.put("source", source);
        }
        if (clientRoute != null && !clientRoute.isBlank()) {
            metadata.put("clientRoute", clientRoute);
        }
        metadata.put("bestEffort", bestEffort);
        return metadata;
    }

}
