package com.example.SKALA_Mini_Project_1.modules.seats.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.SKALA_Mini_Project_1.modules.seats.domain.Seat;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Query(
            value = """
                    SELECT COALESCE(SUM(s.price), 0)
                    FROM ticketing.booking_items bi
                    JOIN concert.seats s ON s.id = bi.seat_id
                    WHERE bi.booking_id = :bookingId
                    """,
            nativeQuery = true
    )
    long sumHeldSeatPrice(@Param("bookingId") UUID bookingId);

    @Query(
            value = """
                    SELECT s.id, s.section, s.row_number, s.seat_number, s.status, s.grade, s.price
                    FROM concert.seats s
                    JOIN concert.schedules sc ON sc.id = s.schedule_id
                    WHERE sc.concert_id = :concertId
                    ORDER BY s.section, s.row_number, s.seat_number
            """,
            nativeQuery = true
    )
    List<Object[]> findSeatMapByConcertId(@Param("concertId") Long concertId);

    @Query(
            value = """
                    SELECT s.id, s.section, s.row_number, s.seat_number, s.status, s.grade, s.price
                    FROM concert.seats s
                    WHERE s.schedule_id = :scheduleId
                    ORDER BY s.section, s.row_number, s.seat_number
            """,
            nativeQuery = true
    )
    List<Object[]> findSeatMapByScheduleId(@Param("scheduleId") Long scheduleId);

    @Query(
            value = """
                    SELECT sc.concert_id
                    FROM concert.schedules sc
                    WHERE sc.id = :scheduleId
            """,
            nativeQuery = true
    )
    Optional<Long> findConcertIdByScheduleId(@Param("scheduleId") Long scheduleId);

    @Query(
            value = """
                    SELECT s.*
                    FROM concert.seats s
                    JOIN concert.schedules sc ON sc.id = s.schedule_id
                    WHERE s.id = :seatId
                      AND sc.concert_id = :concertId
            """,
            nativeQuery = true
    )
    Optional<Seat> findByIdAndConcertId(@Param("seatId") Long seatId, @Param("concertId") Long concertId);

    @Query(
            value = """
                    SELECT s.*
                    FROM concert.seats s
                    WHERE s.id = :seatId
                      AND s.schedule_id = :scheduleId
            """,
            nativeQuery = true
    )
    Optional<Seat> findByIdAndScheduleId(@Param("seatId") Long seatId, @Param("scheduleId") Long scheduleId);

    @Query(
            value = """
                    SELECT s.*
                    FROM concert.seats s
                    JOIN concert.schedules sc ON sc.id = s.schedule_id
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
                    FROM concert.seats s
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
                    FROM concert.seats s
                    JOIN concert.schedules sc ON sc.id = s.schedule_id
                    WHERE s.id = :seatId
                      AND sc.concert_id = :concertId
                      AND s.schedule_id = :scheduleId
            """,
            nativeQuery = true
    )
    Optional<Seat> findByIdAndConcertIdAndScheduleId(
            @Param("seatId") Long seatId,
            @Param("concertId") Long concertId,
            @Param("scheduleId") Long scheduleId
    );

    @Query(
            value = """
                    SELECT s.id AS seatId,
                           s.section AS section,
                           s.row_number AS rowNumber,
                           s.seat_number AS seatNumber,
                           s.price AS price,
                           s.status AS status,
                           s.schedule_id AS scheduleId
                    FROM concert.seats s
                    JOIN concert.schedules sc ON sc.id = s.schedule_id
                    WHERE sc.concert_id = :concertId
                      AND s.id IN (:seatIds)
            """,
            nativeQuery = true
    )
    List<SeatBookingView> findSeatBookingViews(@Param("concertId") Long concertId, @Param("seatIds") List<Long> seatIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    UPDATE concert.seats s
                    SET status = 'RESERVED',
                        version = version + 1
                    WHERE s.id IN (
                        SELECT bi.seat_id
                        FROM ticketing.booking_items bi
                        WHERE bi.booking_id = :bookingId
                    )
                      AND s.status <> 'RESERVED'
                    """,
            nativeQuery = true
    )
    int reserveSeatsByBookingId(@Param("bookingId") UUID bookingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = """
                    UPDATE concert.seats s
                    SET status = 'AVAILABLE',
                        version = version + 1
                    WHERE s.id IN (
                        SELECT bi.seat_id
                        FROM ticketing.booking_items bi
                        WHERE bi.booking_id = :bookingId
                    )
                      AND s.status <> 'AVAILABLE'
                    """,
            nativeQuery = true
    )
    int releaseSeatHoldsByBookingId(@Param("bookingId") UUID bookingId);

    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM concert.seats s
                    JOIN ticketing.booking_items bi ON bi.seat_id = s.id
                    WHERE bi.booking_id = :bookingId
                      AND s.status <> :expectedStatus
                    """,
            nativeQuery = true
    )
    long countSeatsNotInStatusByBookingId(@Param("bookingId") UUID bookingId, @Param("expectedStatus") String expectedStatus);
}
