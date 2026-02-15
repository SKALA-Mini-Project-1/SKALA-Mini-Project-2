package com.example.SKALA_Mini_Project_1.modules.seats.service;

import com.example.SKALA_Mini_Project_1.global.redis.RedisLockRepository;
import com.example.SKALA_Mini_Project_1.modules.seats.dto.SeatMapResponse;
import com.example.SKALA_Mini_Project_1.modules.seats.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SeatMapService {

    private final SeatRepository seatRepository;
    private final RedisLockRepository redisLockRepository;

    public SeatMapResponse getSeatMap(Long concertId, Long userId) {
        List<Object[]> rows = seatRepository.findSeatMapByConcertId(concertId);

        List<SeatMapResponse.SeatItem> seats = rows.stream()
                .map(row -> mapToSeatItem(concertId, userId, row))
                .toList();

        return SeatMapResponse.builder()
                .concertId(concertId)
                .seatCount(seats.size())
                .seats(seats)
                .build();
    }

    private SeatMapResponse.SeatItem mapToSeatItem(Long concertId, Long userId, Object[] row) {
        Long seatId = ((Number) row[0]).longValue();
        String section = (String) row[1];
        Integer rowNumber = ((Number) row[2]).intValue();
        Integer seatNumber = ((Number) row[3]).intValue();
        String status = (String) row[4];
        String grade = (String) row[5];
        BigDecimal price = (BigDecimal) row[6];

        // Redis에 캐싱된 사용자 정보를 가져옴
        String owner = redisLockRepository.getSeatOwner(concertId, seatId);
        Long ttlSeconds = redisLockRepository.getSeatLockTtlSeconds(concertId, seatId);

        Boolean isHeldByMe = null;
        OffsetDateTime holdExpiresAt = null;
        if (owner != null && ttlSeconds != null) {
            isHeldByMe = Objects.equals(owner, String.valueOf(userId));
            holdExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(ttlSeconds);
        }

        return SeatMapResponse.SeatItem.builder()
                .seatId(seatId)
                .section(section)
                .rowNumber(rowNumber)
                .seatNumber(seatNumber)
                .status(status)
                .grade(grade)
                .price(price)
                .isHeldByMe(isHeldByMe)
                .holdExpiresAt(holdExpiresAt)
                .build();
    }
}
