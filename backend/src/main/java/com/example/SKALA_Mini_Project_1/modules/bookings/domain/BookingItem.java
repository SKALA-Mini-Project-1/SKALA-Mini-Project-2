package com.example.SKALA_Mini_Project_1.modules.bookings.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "booking_items")
@Getter
@Setter
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", columnDefinition = "uuid")
    private UUID bookingId;

    @Column(name = "seat_id")
    private Long seatId;
}
