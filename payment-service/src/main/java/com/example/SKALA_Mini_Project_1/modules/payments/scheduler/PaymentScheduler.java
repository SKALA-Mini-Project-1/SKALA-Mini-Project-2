package com.example.SKALA_Mini_Project_1.modules.payments.scheduler;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.SKALA_Mini_Project_1.modules.payments.client.InternalBookingFinalizationResponse;
import com.example.SKALA_Mini_Project_1.modules.payments.client.TicketingFinalizationAction;
import com.example.SKALA_Mini_Project_1.modules.payments.client.TicketingFinalizationClient;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.Payment;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentStatus;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentRepository;
import com.example.SKALA_Mini_Project_1.modules.payments.service.PaymentEventRecorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRecorder paymentEventRecorder;
    private final TicketingFinalizationClient ticketingFinalizationClient;

    // 15초마다 만료 결제를 스캔해 EXPIRED 전환 + booking/seat 복구를 수행한다.
    @Scheduled(fixedDelayString = "${payment.scheduler.expire.fixed-delay-ms:15000}")
    @Transactional
    public void expireTimedOutPayments() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Payment> byExpiredAt = paymentRepository.findTop200ByStatusInAndExpiredAtBeforeOrderByExpiredAtAsc(
                Set.of(PaymentStatus.PENDING, PaymentStatus.PAYING),
                now
        );
        List<Payment> byHardDeadline = paymentRepository.findTop200ByStatusAndHardDeadlineAtBeforeOrderByHardDeadlineAtAsc(
                PaymentStatus.PAYING,
                now
        );

        Map<UUID, Payment> deduped = new LinkedHashMap<>();
        for (Payment payment : byExpiredAt) {
            deduped.put(payment.getId(), payment);
        }
        for (Payment payment : byHardDeadline) {
            deduped.put(payment.getId(), payment);
        }

        for (Payment payment : deduped.values()) {
            try {
                InternalBookingFinalizationResponse response = ticketingFinalizationClient.finalizeBooking(
                        payment,
                        TicketingFinalizationAction.EXPIRE,
                        now,
                        "PAYMENT_TIMEOUT"
                );
                if ("BOOKING_ALREADY_CONFIRMED".equalsIgnoreCase(response.outcome())) {
                    log.info("Skip expiring payment because booking is already confirmed. paymentId={}", payment.getId());
                    continue;
                }
                String from = payment.getStatus().name();
                payment.changeStatus(PaymentStatus.EXPIRED);
                payment.setUpdatedAt(now);
                paymentEventRecorder.record(
                        payment,
                        "PAYMENT_EXPIRED",
                        from,
                        payment.getStatus().name(),
                        null,
                        payment.getPgOrderId()
                );
            } catch (Exception e) {
                log.warn("Skip expiring payment due to ticketing finalization failure. paymentId={}, reason={}",
                        payment.getId(), e.getMessage());
            }
        }
    }
}
