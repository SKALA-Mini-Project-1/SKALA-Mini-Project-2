package com.example.SKALA_Mini_Project_1.modules.payments.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.example.SKALA_Mini_Project_1.global.redis.RedisLockRepository;
import com.example.SKALA_Mini_Project_1.modules.bookings.domain.Booking;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingItemRepository;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingRepository;
import com.example.SKALA_Mini_Project_1.modules.payments.client.TossPaymentsClient;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentEventRepository;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.PaymentRepository;
import com.example.SKALA_Mini_Project_1.modules.payments.repository.RefundRepository;
import com.example.SKALA_Mini_Project_1.modules.seats.repository.SeatRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceHoldValidationTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private TossPaymentsClient tossPaymentsClient;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingItemRepository bookingItemRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PaymentEventRepository paymentEventRepository;
    @Mock
    private RedisLockRepository redisLockRepository;
    @Mock
    private RedisTemplate<String, String> redisTemplate;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                restTemplate,
                tossPaymentsClient,
                bookingRepository,
                bookingItemRepository,
                seatRepository,
                refundRepository,
                paymentEventRepository,
                redisLockRepository,
                redisTemplate
        );
    }

    @Test
    void isHoldValidForPaymentReturnsTrueWhenAllSeatLocksOwnedAndAlive() {
        UUID bookingId = UUID.randomUUID();
        Long userId = 11L;
        Long scheduleId = 21L;
        Long concertId = 31L;

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setUserId(userId);
        booking.setScheduleId(scheduleId);
        booking.setStatus("PENDING");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.findConcertIdByBookingId(bookingId)).thenReturn(Optional.of(concertId));
        when(bookingItemRepository.findSeatIdsByBookingId(bookingId)).thenReturn(List.of(1L, 2L));

        when(redisLockRepository.getSeatOwner(concertId, scheduleId, 1L)).thenReturn("11");
        when(redisLockRepository.getSeatOwner(concertId, scheduleId, 2L)).thenReturn("11");
        when(redisLockRepository.getSeatLockTtlSeconds(concertId, scheduleId, 1L)).thenReturn(120L);
        when(redisLockRepository.getSeatLockTtlSeconds(concertId, scheduleId, 2L)).thenReturn(119L);

        Boolean valid = ReflectionTestUtils.invokeMethod(paymentService, "isHoldValidForPayment", bookingId);

        assertTrue(Boolean.TRUE.equals(valid));
    }

    @Test
    void isHoldValidForPaymentReturnsFalseWhenSeatOwnerMismatch() {
        UUID bookingId = UUID.randomUUID();
        Long userId = 11L;
        Long scheduleId = 21L;
        Long concertId = 31L;

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setUserId(userId);
        booking.setScheduleId(scheduleId);
        booking.setStatus("PENDING");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.findConcertIdByBookingId(bookingId)).thenReturn(Optional.of(concertId));
        when(bookingItemRepository.findSeatIdsByBookingId(bookingId)).thenReturn(List.of(1L));

        when(redisLockRepository.getSeatOwner(concertId, scheduleId, 1L)).thenReturn("99");
        when(redisLockRepository.getSeatLockTtlSeconds(concertId, scheduleId, 1L)).thenReturn(120L);

        Boolean valid = ReflectionTestUtils.invokeMethod(paymentService, "isHoldValidForPayment", bookingId);

        assertFalse(Boolean.TRUE.equals(valid));
    }

    @Test
    void isHoldValidForPaymentReturnsFalseWhenTtlExpired() {
        UUID bookingId = UUID.randomUUID();
        Long userId = 11L;
        Long scheduleId = 21L;
        Long concertId = 31L;

        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setUserId(userId);
        booking.setScheduleId(scheduleId);
        booking.setStatus("PENDING");

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.findConcertIdByBookingId(bookingId)).thenReturn(Optional.of(concertId));
        when(bookingItemRepository.findSeatIdsByBookingId(bookingId)).thenReturn(List.of(1L));

        when(redisLockRepository.getSeatOwner(concertId, scheduleId, 1L)).thenReturn("11");
        when(redisLockRepository.getSeatLockTtlSeconds(concertId, scheduleId, 1L)).thenReturn(0L);

        Boolean valid = ReflectionTestUtils.invokeMethod(paymentService, "isHoldValidForPayment", bookingId);

        assertFalse(Boolean.TRUE.equals(valid));
    }
}
