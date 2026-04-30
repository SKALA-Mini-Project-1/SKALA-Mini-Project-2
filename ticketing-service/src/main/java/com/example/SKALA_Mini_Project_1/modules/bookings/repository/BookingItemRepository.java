package com.example.SKALA_Mini_Project_1.modules.bookings.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.SKALA_Mini_Project_1.modules.bookings.domain.BookingItem;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {
    @Query(
            value = """
                    SELECT bi.seat_id
                    FROM booking_items bi
                    WHERE bi.booking_id = :bookingId
                    ORDER BY bi.seat_id
                    LIMIT 1
                    """,
            nativeQuery = true
    )
    Long findFirstSeatIdByBookingId(@Param("bookingId") UUID bookingId);

    @Query(
            value = """
                    SELECT bi.seat_id
                    FROM booking_items bi
                    WHERE bi.booking_id = :bookingId
                    ORDER BY bi.seat_id
                    """,
            nativeQuery = true
    )
    List<Long> findSeatIdsByBookingId(@Param("bookingId") UUID bookingId);

    @Query(
            value = """
                    SELECT s.id AS seat_id,
                           s.section AS section,
                           s.row_number AS row_number,
                           s.seat_number AS seat_number,
                           s.grade AS grade,
                           s.price AS price
                    FROM booking_items bi
                    JOIN seats s ON s.id = bi.seat_id
                    WHERE bi.booking_id = :bookingId
                    ORDER BY s.section, s.row_number, s.seat_number
                    """,
            nativeQuery = true
    )
    List<Object[]> findBookedSeatDetails(@Param("bookingId") UUID bookingId);
}
