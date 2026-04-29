package com.example.SKALA_Mini_Project_1.modules.finalization.service;

import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import com.example.SKALA_Mini_Project_1.global.redis.RedisLockRepository;
import com.example.SKALA_Mini_Project_1.modules.bookings.domain.Booking;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingItemRepository;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingRepository;
import com.example.SKALA_Mini_Project_1.modules.fanscore.FanScoreService;
import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingCancelRequest;
import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingConfirmRequest;
import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingExpireRequest;
import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingFinalizationResponse;
import com.example.SKALA_Mini_Project_1.modules.seats.repository.SeatRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketingFinalizationService {

    private static final Logger log = LoggerFactory.getLogger(TicketingFinalizationService.class);
    private static final String BOOKING_STATUS_HOLDING = "HOLDING";
    private static final String BOOKING_STATUS_CONFIRMED = "CONFIRMED";
    private static final String BOOKING_STATUS_CANCELED = "CANCELED";

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final SeatRepository seatRepository;
    private final RedisLockRepository redisLockRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final FanScoreService fanScoreService;

    @Transactional
    public InternalBookingFinalizationResponse confirmBooking(InternalBookingConfirmRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(request.bookingId())
                .orElseThrow(() -> new EntityNotFoundException("예약 정보를 찾을 수 없습니다."));

        BookingContext context = loadContext(booking);
        OffsetDateTime processedAt = resolveTime(request.confirmedAt());

        if (BOOKING_STATUS_CONFIRMED.equalsIgnoreCase(booking.getStatus())) {
            return response(
                    "ALREADY_CONFIRMED",
                    booking,
                    context,
                    true,
                    null,
                    request.paymentId(),
                    request.pgOrderId(),
                    request.pgPaymentKey(),
                    request.amount(),
                    processedAt
            );
        }

        if (BOOKING_STATUS_CANCELED.equalsIgnoreCase(booking.getStatus())) {
            return response(
                    "BOOKING_ALREADY_CANCELED",
                    booking,
                    context,
                    false,
                    null,
                    request.paymentId(),
                    request.pgOrderId(),
                    request.pgPaymentKey(),
                    request.amount(),
                    processedAt
            );
        }

        boolean holdValid = isHoldValid(context);
        if (!holdValid) {
            return response(
                    "INVALID_HOLD",
                    booking,
                    context,
                    false,
                    null,
                    request.paymentId(),
                    request.pgOrderId(),
                    request.pgPaymentKey(),
                    request.amount(),
                    processedAt
            );
        }

        seatRepository.reserveSeatsByBookingId(booking.getId());
        booking.setStatus(BOOKING_STATUS_CONFIRMED);
        booking.setConfirmedAt(processedAt);

        fanScoreService.applyConfirmedBookingScore(booking.getId(), booking.getUserId());
        scheduleSeatCleanupAfterCommit(context.concertId(), booking.getScheduleId(), booking.getUserId());

        return response(
                "CONFIRMED",
                booking,
                context,
                true,
                null,
                request.paymentId(),
                request.pgOrderId(),
                request.pgPaymentKey(),
                request.amount(),
                processedAt
        );
    }

    @Transactional
    public InternalBookingFinalizationResponse cancelBooking(InternalBookingCancelRequest request) {
        return closeBooking(
                request.bookingId(),
                request.paymentId(),
                request.pgOrderId(),
                request.reasonCode(),
                resolveTime(request.canceledAt()),
                "CANCELED_BY_PAYMENT",
                "ALREADY_CANCELED"
        );
    }

    @Transactional
    public InternalBookingFinalizationResponse expireBooking(InternalBookingExpireRequest request) {
        return closeBooking(
                request.bookingId(),
                request.paymentId(),
                request.pgOrderId(),
                request.reasonCode(),
                resolveTime(request.expiredAt()),
                "EXPIRED_BY_PAYMENT",
                "ALREADY_EXPIRED_OR_CANCELED"
        );
    }

    private InternalBookingFinalizationResponse closeBooking(
            UUID bookingId,
            UUID paymentId,
            String pgOrderId,
            String reasonCode,
            OffsetDateTime processedAt,
            String successOutcome,
            String noopOutcome
    ) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("예약 정보를 찾을 수 없습니다."));

        BookingContext context = loadContext(booking);

        if (BOOKING_STATUS_CONFIRMED.equalsIgnoreCase(booking.getStatus())) {
            return response(
                    "BOOKING_ALREADY_CONFIRMED",
                    booking,
                    context,
                    false,
                    reasonCode,
                    paymentId,
                    pgOrderId,
                    null,
                    null,
                    processedAt
            );
        }

        if (BOOKING_STATUS_CANCELED.equalsIgnoreCase(booking.getStatus())) {
            return response(
                    noopOutcome,
                    booking,
                    context,
                    false,
                    reasonCode,
                    paymentId,
                    pgOrderId,
                    null,
                    null,
                    processedAt
            );
        }

        booking.setStatus(BOOKING_STATUS_CANCELED);
        booking.setCanceledAt(processedAt);

        seatRepository.releaseSeatHoldsByBookingId(bookingId);
        scheduleSeatCleanupAfterCommit(context.concertId(), booking.getScheduleId(), booking.getUserId());

        return response(
                successOutcome,
                booking,
                context,
                false,
                reasonCode,
                paymentId,
                pgOrderId,
                null,
                null,
                processedAt
        );
    }

    private BookingContext loadContext(Booking booking) {
        Long concertId = bookingRepository.findConcertIdByBookingId(booking.getId())
                .orElseThrow(() -> new IllegalStateException("예약의 콘서트 정보를 찾을 수 없습니다."));
        List<Long> seatIds = bookingItemRepository.findSeatIdsByBookingId(booking.getId());
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalStateException("예약 좌석 정보가 비어 있습니다.");
        }

        return new BookingContext(
                concertId,
                booking.getScheduleId(),
                booking.getUserId(),
                seatIds
        );
    }

    private boolean isHoldValid(BookingContext context) {
        if (context.scheduleId() == null || context.userId() == null) {
            return false;
        }

        String expectedOwner = String.valueOf(context.userId());
        for (Long seatId : context.seatIds()) {
            String owner = redisLockRepository.getSeatOwner(context.concertId(), context.scheduleId(), seatId);
            Long ttl = redisLockRepository.getSeatLockTtlSeconds(context.concertId(), context.scheduleId(), seatId);
            if (!expectedOwner.equals(owner) || ttl == null || ttl <= 0) {
                return false;
            }
        }
        return true;
    }

    private OffsetDateTime resolveTime(OffsetDateTime requestedAt) {
        return requestedAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : requestedAt;
    }

    private InternalBookingFinalizationResponse response(
            String outcome,
            Booking booking,
            BookingContext context,
            boolean holdValid,
            String reasonCode,
            UUID paymentId,
            String pgOrderId,
            String pgPaymentKey,
            Long amount,
            OffsetDateTime processedAt
    ) {
        return new InternalBookingFinalizationResponse(
                booking.getId(),
                paymentId,
                outcome,
                booking.getStatus(),
                holdValid,
                booking.getUserId(),
                context.concertId(),
                booking.getScheduleId(),
                context.seatIds(),
                reasonCode,
                pgOrderId,
                pgPaymentKey,
                amount,
                processedAt
        );
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

    private record BookingContext(
            Long concertId,
            Long scheduleId,
            Long userId,
            List<Long> seatIds
    ) {
    }
}
