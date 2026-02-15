package com.example.SKALA_Mini_Project_1.modules.bookings.repository;

import com.example.SKALA_Mini_Project_1.modules.bookings.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
}
