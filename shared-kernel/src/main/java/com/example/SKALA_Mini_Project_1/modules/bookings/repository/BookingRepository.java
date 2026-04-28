package com.example.SKALA_Mini_Project_1.modules.bookings.repository;

import com.example.SKALA_Mini_Project_1.modules.bookings.domain.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByIdAndUserId(UUID id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    interface BookingConcertInfo {
        String getConcertTitle();
        String getConcertVenue();
        Instant getShowTime();
    }

    @Query(
            value = """
                    SELECT c.title AS concertTitle,
                           c.location AS concertVenue,
                           s.start_time AS showTime
                    FROM bookings b
                    JOIN schedules s ON s.id = b.schedule_id
                    JOIN concerts c ON c.id = s.concert_id
                    WHERE b.id = :bookingId
            """,
            nativeQuery = true
    )
    Optional<BookingConcertInfo> findBookingConcertInfo(@Param("bookingId") UUID bookingId);

    @Query(
            value = """
                    SELECT s.concert_id
                    FROM bookings b
                    JOIN schedules s ON s.id = b.schedule_id
                    WHERE b.id = :bookingId
                    """,
            nativeQuery = true
    )
    Optional<Long> findConcertIdByBookingId(@Param("bookingId") UUID bookingId);
}
