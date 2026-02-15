package com.example.SKALA_Mini_Project_1.modules.bookings.repository;

import com.example.SKALA_Mini_Project_1.modules.bookings.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByIdAndUserId(UUID id, Long userId);

    interface BookingConcertInfo {
        String getConcertTitle();
        Instant getShowTime();
    }

    @Query(
            value = """
                    SELECT c.title AS concertTitle,
                           s.start_time AS showTime
                    FROM bookings b
                    JOIN schedules s ON s.id = b.schedule_id
                    JOIN concerts c ON c.id = s.concert_id
                    WHERE b.id = :bookingId
                    """,
            nativeQuery = true
    )
    Optional<BookingConcertInfo> findBookingConcertInfo(UUID bookingId);
}
