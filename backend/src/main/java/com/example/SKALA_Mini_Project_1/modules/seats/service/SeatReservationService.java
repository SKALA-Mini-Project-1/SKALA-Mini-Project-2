package com.example.SKALA_Mini_Project_1.modules.seats.service;

import com.example.SKALA_Mini_Project_1.global.redis.RedisLockRepository;
import com.example.SKALA_Mini_Project_1.modules.seats.domain.Seat;
import com.example.SKALA_Mini_Project_1.modules.seats.domain.SeatStatus;
import com.example.SKALA_Mini_Project_1.modules.seats.repository.SeatRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatReservationService {
    private static final Logger log = LoggerFactory.getLogger(SeatReservationService.class);
    private static final int MAX_HOLD_SEAT_COUNT = 4;

    public enum SeatHoldResult {
        HELD,
        RELEASED
    }

    public enum SeatReleaseResult {
        RELEASED,
        ALREADY_RELEASED
    }

    public record BatchHoldResult(boolean success, List<Long> heldSeatIds, List<Long> failedSeatIds) {}

    private final SeatRepository seatRepository;
    private final RedisLockRepository redisLockRepository;

    public SeatHoldResult reserveSeatTemporary(Long scheduleId, Long seatId, Long userId) {
        Seat seat = validateSeat(scheduleId, seatId);
        Long concertId = findConcertIdByScheduleId(scheduleId);
        if (seat.getStatus() == SeatStatus.RESERVED) {
            throw new IllegalStateException("이미 판매된 좌석입니다.");
        }

        String userIdString = String.valueOf(userId);
        String currentOwner = redisLockRepository.getSeatOwner(concertId, scheduleId, seatId);

        if (Objects.equals(currentOwner, userIdString)) {
            redisLockRepository.unlockSeat(concertId, scheduleId, seatId);
            log.info("좌석 {} 선점 해제 성공 (사용자: {})", seatId, userId);
            return SeatHoldResult.RELEASED;
        }

        if (currentOwner != null) {
            throw new IllegalStateException("이미 다른 사용자가 선택한 좌석입니다.");
        }

        int currentHoldCount = redisLockRepository.countUserHeldSeats(concertId, scheduleId, userIdString);
        if (currentHoldCount >= MAX_HOLD_SEAT_COUNT) {
            throw new IllegalArgumentException("좌석은 최대 4매까지만 선택할 수 있습니다.");
        }

        boolean isLocked = redisLockRepository.lockSeat(concertId, scheduleId, seatId, userIdString);
        if (!isLocked) {
            throw new IllegalStateException("이미 다른 사용자가 선택한 좌석입니다.");
        }

        log.info("좌석 {} 선점 성공 (사용자: {})", seatId, userId);
        return SeatHoldResult.HELD;
    }

    public BatchHoldResult holdSeatsBatch(Long concertId, List<Long> seatIds, Long userId) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("좌석 ID 목록은 비어 있을 수 없습니다.");
        }

        Set<Long> uniqueSeatIds = new LinkedHashSet<>(seatIds);
        if (uniqueSeatIds.size() > MAX_HOLD_SEAT_COUNT) {
            throw new IllegalArgumentException("좌석은 최대 4매까지만 선택할 수 있습니다.");
        }

        String userIdString = String.valueOf(userId);
        List<Seat> seats = new ArrayList<>();
        List<Long> failedSeatIds = new ArrayList<>();
        for (Long seatId : uniqueSeatIds) {
            Seat seat = seatRepository.findByIdAndConcertId(seatId, concertId).orElse(null);
            if (seat == null || seat.getStatus() == SeatStatus.RESERVED) {
                failedSeatIds.add(seatId);
                continue;
            }
            seats.add(seat);
        }

        if (!failedSeatIds.isEmpty()) {
            return new BatchHoldResult(false, List.of(), failedSeatIds);
        }

        Set<Long> scheduleIds = seats.stream().map(Seat::getScheduleId).collect(Collectors.toSet());
        if (scheduleIds.size() != 1) {
            throw new IllegalArgumentException("서로 다른 회차의 좌석은 함께 선택할 수 없습니다.");
        }
        Long scheduleId = scheduleIds.iterator().next();
        int currentHoldCount = redisLockRepository.countUserHeldSeats(concertId, scheduleId, userIdString);

        List<Seat> seatsToLock = new ArrayList<>();
        List<Long> alreadyHeldByMe = new ArrayList<>();
        for (Seat seat : seats) {
            String owner = redisLockRepository.getSeatOwner(concertId, scheduleId, seat.getId());
            if (owner == null) {
                seatsToLock.add(seat);
                continue;
            }
            if (Objects.equals(owner, userIdString)) {
                alreadyHeldByMe.add(seat.getId());
            } else {
                failedSeatIds.add(seat.getId());
            }
        }

        if (!failedSeatIds.isEmpty()) {
            return new BatchHoldResult(false, List.of(), failedSeatIds);
        }

        if (currentHoldCount + seatsToLock.size() > MAX_HOLD_SEAT_COUNT) {
            throw new IllegalArgumentException("좌석은 최대 4매까지만 선택할 수 있습니다.");
        }

        List<Long> newlyLockedSeatIds = new ArrayList<>();
        for (Seat seat : seatsToLock) {
            boolean locked = redisLockRepository.lockSeat(concertId, scheduleId, seat.getId(), userIdString);
            if (!locked) {
                failedSeatIds.add(seat.getId());
                for (Long lockedSeatId : newlyLockedSeatIds) {
                    redisLockRepository.unlockSeat(concertId, scheduleId, lockedSeatId);
                }
                return new BatchHoldResult(false, List.of(), failedSeatIds);
            }
            newlyLockedSeatIds.add(seat.getId());
        }

        List<Long> heldSeatIds = new ArrayList<>(alreadyHeldByMe);
        heldSeatIds.addAll(newlyLockedSeatIds);
        return new BatchHoldResult(true, heldSeatIds, List.of());
    }

    public SeatReleaseResult releaseSeatHold(Long scheduleId, Long seatId, Long userId) {
        validateSeat(scheduleId, seatId);
        Long concertId = findConcertIdByScheduleId(scheduleId);

        String owner = redisLockRepository.getSeatOwner(concertId, scheduleId, seatId);
        if (owner == null) {
            return SeatReleaseResult.ALREADY_RELEASED;
        }

        if (!Objects.equals(owner, String.valueOf(userId))) {
            throw new IllegalStateException("다른 사용자가 선점한 좌석은 해제할 수 없습니다.");
        }

        redisLockRepository.unlockSeat(concertId, scheduleId, seatId);
        return SeatReleaseResult.RELEASED;
    }

    private Seat validateSeat(Long scheduleId, Long seatId) {
        return seatRepository.findByIdAndScheduleId(seatId, scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("좌석이 존재하지 않습니다. ID: " + seatId));
    }

    private Long findConcertIdByScheduleId(Long scheduleId) {
        return seatRepository.findConcertIdByScheduleId(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("스케줄이 존재하지 않습니다. ID: " + scheduleId));
    }
}
