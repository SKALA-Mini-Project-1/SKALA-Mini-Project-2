package com.example.SKALA_Mini_Project_1.modules.bookings.repository;

import com.example.SKALA_Mini_Project_1.modules.bookings.domain.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {
}
