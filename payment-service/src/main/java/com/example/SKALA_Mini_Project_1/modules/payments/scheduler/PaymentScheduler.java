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
import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentEvent;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.Payment;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentStatus;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentEventRepository;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final TicketingFinalizationClient ticketingFinalizationClient;

    // 15초마다 만료 결제를 스캔해 EXPIRED 전환 + booking/seat 복구를 수행한다.
    @Scheduled(fixedDelay = 15000)
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
                    System.out.println("[PaymentScheduler] skip payment " + payment.getId()
                            + ": booking already confirmed");
                    continue;
                }
                String from = payment.getStatus().name();
                payment.changeStatus(PaymentStatus.EXPIRED);
                payment.setUpdatedAt(now);
                recordEvent(payment, "PAYMENT_EXPIRED", from, payment.getStatus().name(), payment.getPgOrderId());
            } catch (Exception e) {
                System.out.println("[PaymentScheduler] skip payment " + payment.getId() + ": " + e.getMessage());
            }
        }
    }

    private void recordEvent(Payment payment, String eventType, String fromStatus, String toStatus, String pgEventId) {
        PaymentEvent ev = new PaymentEvent();
        ev.setPaymentId(payment.getId());
        ev.setEventType(eventType);
        ev.setFromStatus(fromStatus);
        ev.setToStatus(toStatus);
        ev.setIdempotencyKey(payment.getIdempotencyKey());
        ev.setPgEventId(pgEventId);
        ev.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        paymentEventRepository.save(ev);
    }
}
