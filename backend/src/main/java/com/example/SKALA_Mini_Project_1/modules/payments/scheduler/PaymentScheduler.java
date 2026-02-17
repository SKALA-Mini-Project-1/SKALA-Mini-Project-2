package com.example.SKALA_Mini_Project_1.modules.payments.scheduler;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.SKALA_Mini_Project_1.modules.bookings.domain.Booking;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingRepository;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.Payment;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentStatus;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentRepository;
import com.example.SKALA_Mini_Project_1.modules.seats.repository.SeatRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentScheduler {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;

    // 15초마다 만료 결제를 스캔해 EXPIRED 전환 + booking/seat 복구를 수행한다.
    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void expireTimedOutPayments() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Payment> targets = paymentRepository.findTop200ByStatusInAndExpiredAtBeforeOrderByExpiredAtAsc(
                Set.of(PaymentStatus.PENDING, PaymentStatus.PAYING),
                now
        );

        for (Payment payment : targets) {
            try {
                payment.changeStatus(PaymentStatus.EXPIRED);
                payment.setUpdatedAt(now);
                expireBookingAndReleaseSeats(payment.getBookingId(), now);
            } catch (Exception e) {
                System.out.println("[PaymentScheduler] skip payment " + payment.getId() + ": " + e.getMessage());
            }
        }
    }

    private void expireBookingAndReleaseSeats(java.util.UUID bookingId, OffsetDateTime now) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if ("CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
            return;
        }

        booking.setStatus("CANCELED");
        booking.setCanceledAt(now);

        seatRepository.releaseSeatHoldsByBookingId(bookingId);
    }
}
