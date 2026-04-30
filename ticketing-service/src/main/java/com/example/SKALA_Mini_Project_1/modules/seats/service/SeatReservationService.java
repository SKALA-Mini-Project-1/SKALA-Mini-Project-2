package com.example.SKALA_Mini_Project_1.modules.seats.service;

import com.example.SKALA_Mini_Project_1.global.redis.RedisLockRepository;
import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import com.example.SKALA_Mini_Project_1.modules.seats.domain.Seat;
import com.example.SKALA_Mini_Project_1.modules.seats.domain.SeatStatus;
import com.example.SKALA_Mini_Project_1.modules.seats.repository.SeatRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    public record LeaveSeatScreenResult(int releasedSeatCount, boolean activeDecremented, long activeCount) {}

    private final SeatRepository seatRepository;
    private final RedisLockRepository redisLockRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public SeatHoldResult reserveSeatTemporary(Long scheduleId, Long seatId, Long userId) {
        Seat seat = validateSeat(scheduleId, seatId);
        Long concertId = findConcertIdByScheduleId(scheduleId);
        if (seat.getStatus() == SeatStatus.RESERVED) {
            throw new IllegalStateException("이미 판매된 좌석입니다.");
        }

        String userIdString = String.valueOf(userId);
        String currentOwner = redisLockRepository.getSeatOwner(concertId, scheduleId, seatId);

        if (Objects.equals(currentOwner, userIdString)) {
            redisLockRepository.unlockSeatIfOwner(concertId, scheduleId, seatId, userIdString);
            log.info("좌석 {} 선점 해제 성공 (사용자: {})", seatId, userId);
            return SeatHoldResult.RELEASED;
        }

        if (currentOwner != null) {
            throw new IllegalStateException("이미 다른 사용자가 선택한 좌석입니다.");
        }

        RedisLockRepository.SeatLockWithLimitResult lockResult = redisLockRepository.lockSeatWithLimit(
                concertId,
                scheduleId,
                seatId,
                userIdString,
                MAX_HOLD_SEAT_COUNT
        );
        if (lockResult == RedisLockRepository.SeatLockWithLimitResult.LIMIT_EXCEEDED) {
            throw new IllegalArgumentException("좌석은 최대 4매까지만 선택할 수 있습니다.");
        }
        if (lockResult != RedisLockRepository.SeatLockWithLimitResult.LOCKED) {
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

        List<Long> newlyLockedSeatIds = new ArrayList<>();
        for (Seat seat : seatsToLock) {
            RedisLockRepository.SeatLockWithLimitResult lockResult = redisLockRepository.lockSeatWithLimit(
                    concertId,
                    scheduleId,
                    seat.getId(),
                    userIdString,
                    MAX_HOLD_SEAT_COUNT
            );
            if (lockResult == RedisLockRepository.SeatLockWithLimitResult.LIMIT_EXCEEDED) {
                for (Long lockedSeatId : newlyLockedSeatIds) {
                    redisLockRepository.unlockSeatIfOwner(concertId, scheduleId, lockedSeatId, userIdString);
                }
                throw new IllegalArgumentException("좌석은 최대 4매까지만 선택할 수 있습니다.");
            }
            if (lockResult != RedisLockRepository.SeatLockWithLimitResult.LOCKED) {
                failedSeatIds.add(seat.getId());
                for (Long lockedSeatId : newlyLockedSeatIds) {
                    redisLockRepository.unlockSeatIfOwner(concertId, scheduleId, lockedSeatId, userIdString);
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

        boolean released = redisLockRepository.unlockSeatIfOwner(concertId, scheduleId, seatId, String.valueOf(userId));
        return released ? SeatReleaseResult.RELEASED : SeatReleaseResult.ALREADY_RELEASED;
    }

    public int releaseAllSeatHolds(Long scheduleId, Long userId) {
        Long concertId = findConcertIdByScheduleId(scheduleId);
        return redisLockRepository.releaseUserHeldSeats(concertId, scheduleId, String.valueOf(userId));
    }

    public Long resolveSeatId(Long scheduleId, String section, Integer rowNumber, Integer seatNumber) {
        Seat seat = seatRepository
                .findByScheduleIdAndSectionAndRowNumberAndSeatNumber(scheduleId, section, rowNumber, seatNumber)
                .orElseThrow(() -> new EntityNotFoundException(
                        "좌석이 존재하지 않습니다. section=%s, row=%d, seat=%d".formatted(
                                section,
                                rowNumber,
                                seatNumber
                        )
                ));
        return seat.getId();
    }

    public LeaveSeatScreenResult leaveSeatScreen(Long concertId, Long scheduleId, Long userId) {
        Long resolvedConcertId = findConcertIdByScheduleId(scheduleId);
        if (!resolvedConcertId.equals(concertId)) {
            throw new IllegalArgumentException("콘서트/회차 정보가 일치하지 않습니다.");
        }

        int releasedSeatCount = releaseAllSeatHolds(scheduleId, userId);

        String accessKey = RedisKeyGenerator.seatAccessKey(userId, concertId, scheduleId);
        String accessByScheduleKey = RedisKeyGenerator.seatAccessByScheduleKey(userId, scheduleId);
        boolean hadAccess = Boolean.TRUE.equals(redisTemplate.hasKey(accessKey));

        redisTemplate.delete(accessKey);
        redisTemplate.delete(accessByScheduleKey);
        redisTemplate.opsForSet().remove(
                RedisKeyGenerator.seatAccessIndexKey(concertId, scheduleId),
                String.valueOf(userId)
        );

        Long active = null;
        if (hadAccess) {
            active = redisLockRepository.decrementSeatActiveFloorZero(concertId, scheduleId);
        }

        return new LeaveSeatScreenResult(
                releasedSeatCount,
                hadAccess,
                active == null ? -1L : active
        );
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
