package com.example.SKALA_Mini_Project_1.modules.seats.service;

import com.example.SKALA_Mini_Project_1.global.redis.RedisLockRepository;
import com.example.SKALA_Mini_Project_1.modules.seats.dto.SeatMapResponse;
import com.example.SKALA_Mini_Project_1.modules.seats.dto.SeatSectionDetailResponse;
import com.example.SKALA_Mini_Project_1.modules.seats.dto.SeatSectionSummaryResponse;
import com.example.SKALA_Mini_Project_1.modules.seats.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SeatMapService {
    private final SeatRepository seatRepository;
    private final RedisLockRepository redisLockRepository;

    public SeatSectionSummaryResponse getSeatSectionSummaryBySchedule(
            Long scheduleId,
            Long userId,
            Long seatAccessTtlSeconds
    ) {
        Long concertId = findConcertIdByScheduleId(scheduleId);
        List<Object[]> rows = seatRepository.findSeatSectionSummariesByScheduleId(scheduleId);
        List<SeatSectionSummaryResponse.SectionSummaryItem> sections = rows.stream()
                .map(this::mapToSectionSummaryItem)
                .toList();

        int totalSeatCount = sections.stream()
                .mapToInt(SeatSectionSummaryResponse.SectionSummaryItem::getSeatCount)
                .sum();

        return SeatSectionSummaryResponse.builder()
                .concertId(concertId)
                .scheduleId(scheduleId)
                .totalSeatCount(totalSeatCount)
                .sectionCount(sections.size())
                .seatAccessTtlSeconds(seatAccessTtlSeconds)
                .sections(sections)
                .build();
    }

    public SeatSectionDetailResponse getSeatSectionDetailBySchedule(
            Long scheduleId,
            String section,
            Long userId,
            Long seatAccessTtlSeconds
    ) {
        Long concertId = findConcertIdByScheduleId(scheduleId);
        List<Object[]> rows = seatRepository.findSeatMapByScheduleIdAndSection(scheduleId, section);

        if (rows.isEmpty()) {
            return SeatSectionDetailResponse.builder()
                    .concertId(concertId)
                    .scheduleId(scheduleId)
                    .section(section)
                    .seatCount(0)
                    .seatAccessTtlSeconds(seatAccessTtlSeconds)
                    .seats(Collections.emptyList())
                    .build();
        }

        List<Long> seatIds = rows.stream()
                .map(row -> ((Number) row[0]).longValue())
                .toList();
        Map<Long, RedisLockRepository.SeatLockInfo> lockInfoMap =
                redisLockRepository.batchGetSeatLockInfo(concertId, scheduleId, seatIds);
        List<SeatMapResponse.SeatItem> seats = rows.stream()
                .map(row -> mapToSeatItem(userId, row, lockInfoMap))
                .toList();

        return SeatSectionDetailResponse.builder()
                .concertId(concertId)
                .scheduleId(scheduleId)
                .section(section)
                .seatCount(seats.size())
                .seatAccessTtlSeconds(seatAccessTtlSeconds)
                .seats(seats)
                .build();
    }

    public SeatMapResponse getSeatMap(Long concertId, Long scheduleId, Long userId, Long seatAccessTtlSeconds) {
        List<Object[]> rows = seatRepository.findSeatMapByScheduleId(scheduleId);

        // 모든 seatId를 한 번에 추출하여 Redis 배치 조회 (N×2 개별 호출 → 2회 왕복으로 단축)
        List<Long> seatIds = rows.stream()
                .map(row -> ((Number) row[0]).longValue())
                .toList();
        Map<Long, RedisLockRepository.SeatLockInfo> lockInfoMap =
                redisLockRepository.batchGetSeatLockInfo(concertId, scheduleId, seatIds);
        List<SeatMapResponse.SeatItem> seats = rows.stream()
                .map(row -> mapToSeatItem(userId, row, lockInfoMap))
                .toList();

        return SeatMapResponse.builder()
                .concertId(concertId)
                .seatCount(seats.size())
                .seatAccessTtlSeconds(seatAccessTtlSeconds)
                .seats(seats)
                .build();
    }

    public SeatMapResponse getSeatMapBySchedule(Long scheduleId, Long userId, Long seatAccessTtlSeconds) {
        Long concertId = findConcertIdByScheduleId(scheduleId);
        return getSeatMap(concertId, scheduleId, userId, seatAccessTtlSeconds);
    }

    private Long findConcertIdByScheduleId(Long scheduleId) {
        return seatRepository.findConcertIdByScheduleId(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 scheduleId입니다. " + scheduleId));
    }

    private SeatSectionSummaryResponse.SectionSummaryItem mapToSectionSummaryItem(Object[] row) {
        return SeatSectionSummaryResponse.SectionSummaryItem.builder()
                .section((String) row[0])
                .seatCount(((Number) row[1]).intValue())
                .reservedSeatCount(((Number) row[2]).intValue())
                .availableSeatCount(((Number) row[3]).intValue())
                .rowCount(((Number) row[4]).intValue())
                .colCount(((Number) row[5]).intValue())
                .grade((String) row[6])
                .price((BigDecimal) row[7])
                .build();
    }

    private SeatMapResponse.SeatItem mapToSeatItem(Long userId, Object[] row,
                                                    Map<Long, RedisLockRepository.SeatLockInfo> lockInfoMap) {
        Long seatId = ((Number) row[0]).longValue();
        String section = (String) row[1];
        Integer rowNumber = ((Number) row[2]).intValue();
        Integer seatNumber = ((Number) row[3]).intValue();
        String status = (String) row[4];
        String grade = (String) row[5];
        BigDecimal price = (BigDecimal) row[6];

        RedisLockRepository.SeatLockInfo lockInfo = lockInfoMap.get(seatId);

        Boolean isHeldByMe = null;
        OffsetDateTime holdExpiresAt = null;
        String displayStatus = status;
        if (lockInfo != null) {
            isHeldByMe = Objects.equals(lockInfo.owner(), String.valueOf(userId));
            holdExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(lockInfo.ttlSeconds());
            if ("AVAILABLE".equals(status) && Boolean.FALSE.equals(isHeldByMe)) {
                displayStatus = "HELD_BY_OTHER";
            }
        }

        return SeatMapResponse.SeatItem.builder()
                .seatId(seatId)
                .section(section)
                .rowNumber(rowNumber)
                .seatNumber(seatNumber)
                .status(status)
                .displayStatus(displayStatus)
                .grade(grade)
                .price(price)
                .isHeldByMe(isHeldByMe)
                .holdExpiresAt(holdExpiresAt)
                .build();
    }
}
