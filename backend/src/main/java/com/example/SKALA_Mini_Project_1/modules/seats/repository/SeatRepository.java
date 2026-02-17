package com.example.SKALA_Mini_Project_1.modules.seats.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.SKALA_Mini_Project_1.modules.seats.domain.Seat;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

        // 결제 시, 해당 예약에 포함된 좌석들의 가격 합계를 조회하는 메서드 (결제 금액 계산용)
        @Query(
                value = """
                        SELECT COALESCE(SUM(s.price), 0)
                        FROM booking_items bi
                        JOIN seats s ON s.id = bi.seat_id
                        WHERE bi.booking_id = :bookingId
                        """,
                nativeQuery = true
        )
        long sumHeldSeatPrice(@Param("bookingId") UUID bookingId);

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
    List<Object[]> findSeatMapByConcertId(@Param("concertId") Long concertId);

    @Query(
            value = """
                    SELECT s.id, s.section, s.row_number, s.seat_number, s.status, s.grade, s.price
                    FROM seats s
                    WHERE s.schedule_id = :scheduleId
                    ORDER BY s.section, s.row_number, s.seat_number
            """,
            nativeQuery = true
    )
    List<Object[]> findSeatMapByScheduleId(@Param("scheduleId") Long scheduleId);

    @Query(
            value = """
                    SELECT sc.concert_id
                    FROM schedules sc
                    WHERE sc.id = :scheduleId
            """,
            nativeQuery = true
    )
    Optional<Long> findConcertIdByScheduleId(@Param("scheduleId") Long scheduleId);

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
    Optional<Seat> findByIdAndConcertId(
            @Param("seatId") Long seatId,
            @Param("concertId") Long concertId
    );

    @Query(
            value = """
                    SELECT s.*
                    FROM seats s
                    WHERE s.id = :seatId
                      AND s.schedule_id = :scheduleId
            """,
            nativeQuery = true
    )
    Optional<Seat> findByIdAndScheduleId(
            @Param("seatId") Long seatId,
            @Param("scheduleId") Long scheduleId
    );

    @Query(
            value = """
                    SELECT s.*
                    FROM seats s
                    JOIN schedules sc ON sc.id = s.schedule_id
                    WHERE sc.concert_id = :concertId
                      AND s.section = :section
                      AND s.row_number = :rowNumber
                      AND s.seat_number = :seatNumber
                    """,
            nativeQuery = true
    )
    Optional<Seat> findByConcertIdAndSectionAndRowNumberAndSeatNumber(
            @Param("concertId") Long concertId,
            @Param("section") String section,
            @Param("rowNumber") Integer rowNumber,
            @Param("seatNumber") Integer seatNumber
    );

    @Query(
            value = """
                    SELECT s.*
                    FROM seats s
                    WHERE s.schedule_id = :scheduleId
                      AND s.section = :section
                      AND s.row_number = :rowNumber
                      AND s.seat_number = :seatNumber
                    """,
            nativeQuery = true
    )
    Optional<Seat> findByScheduleIdAndSectionAndRowNumberAndSeatNumber(
            @Param("scheduleId") Long scheduleId,
            @Param("section") String section,
            @Param("rowNumber") Integer rowNumber,
            @Param("seatNumber") Integer seatNumber
    );

    @Query(
            value = """
                    SELECT s.*
                    FROM seats s
                    JOIN schedules sc ON sc.id = s.schedule_id
                    WHERE s.id = :seatId
                      AND sc.concert_id = :concertId
                      AND s.schedule_id = :scheduleId
                    """,
            nativeQuery = true
    )
    Optional<Seat> findByIdAndConcertIdAndScheduleId(Long seatId, Long concertId, Long scheduleId);

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
    List<SeatBookingView> findSeatBookingViews(
            @Param("concertId") Long concertId,
            @Param("seatIds") List<Long> seatIds
    );
}
