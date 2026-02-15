package com.example.SKALA_Mini_Project_1.modules.seats.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.SKALA_Mini_Project_1.modules.seats.domain.Seat;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    // 좌석 예매 시, 콘서트별 본인이 예매한 좌석 조회용 메서드
    @Query(
            value = """
                    SELECT s.id, s.section, s.row_number, s.seat_number, s.status, s.grade, s.price
                    FROM seats s
                    JOIN schedules sc ON sc.id = s.schedule_id
                    WHERE sc.concert_id = :concertId
                    ORDER BY s.section, s.row_number, s.seat_number
                    """,
            nativeQuery = true
    )
    List<Object[]> findSeatMapByConcertId(Long concertId);

    @Query(
            value = """
                    SELECT s.*
                    FROM seats s
                    JOIN schedules sc ON sc.id = s.schedule_id
                    WHERE s.id = :seatId
                      AND sc.concert_id = :concertId
                    """,
            nativeQuery = true
    )
    Optional<Seat> findByIdAndConcertId(Long seatId, Long concertId);

    @Query(
            value = """
                    SELECT s.id AS seatId,
                           s.section AS section,
                           s.row_number AS rowNumber,
                           s.seat_number AS seatNumber,
                           s.price AS price,
                           s.status AS status,
                           s.schedule_id AS scheduleId
                    FROM seats s
                    JOIN schedules sc ON sc.id = s.schedule_id
                    WHERE sc.concert_id = :concertId
                      AND s.id IN (:seatIds)
                    """,
            nativeQuery = true
    )
    List<SeatBookingView> findSeatBookingViews(Long concertId, List<Long> seatIds);
}
