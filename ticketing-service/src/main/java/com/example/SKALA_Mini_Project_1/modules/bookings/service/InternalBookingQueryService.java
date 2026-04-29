package com.example.SKALA_Mini_Project_1.modules.bookings.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.SKALA_Mini_Project_1.modules.bookings.dto.InternalBookedSeatDetailResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.dto.InternalBookingHistoryDetailResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.dto.InternalBookingHistoryDetailsResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.dto.InternalBookingPaymentContextResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.dto.InternalUserBookingIdsResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingItemRepository;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InternalBookingQueryService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;

    @Transactional(readOnly = true)
    public InternalBookingPaymentContextResponse getPaymentContext(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .map(booking -> new InternalBookingPaymentContextResponse(
                        booking.getId(),
                        booking.getUserId(),
                        booking.getScheduleId(),
                        booking.getTotalPrice(),
                        booking.getStatus(),
                        booking.getExpiresAt(),
                        booking.getConfirmedAt(),
                        booking.getCanceledAt()
                ))
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));
    }

    @Transactional(readOnly = true)
    public InternalUserBookingIdsResponse getUserBookingIds(Long userId) {
        return new InternalUserBookingIdsResponse(userId, bookingRepository.findBookingIdsByUserId(userId));
    }

    @Transactional(readOnly = true)
    public InternalBookingHistoryDetailsResponse getHistoryDetails(List<UUID> bookingIds) {
        List<InternalBookingHistoryDetailResponse> items = new ArrayList<>();

        if (bookingIds == null || bookingIds.isEmpty()) {
            return new InternalBookingHistoryDetailsResponse(items);
        }

        for (UUID bookingId : bookingIds) {
            bookingRepository.findById(bookingId).ifPresent(booking -> {
                BookingRepository.BookingConcertInfo concertInfo =
                        bookingRepository.findBookingConcertInfo(bookingId).orElse(null);

                List<Object[]> seatRows = bookingItemRepository.findBookedSeatDetails(bookingId);
                List<InternalBookedSeatDetailResponse> seats = new ArrayList<>();
                List<String> seatLabels = new ArrayList<>();

                for (Object[] row : seatRows) {
                    String section = row[1] == null ? null : row[1].toString();
                    Integer rowNumber = toInteger(row[2]);
                    Integer seatNumber = toInteger(row[3]);
                    seats.add(new InternalBookedSeatDetailResponse(
                            toLong(row[0]),
                            section,
                            rowNumber,
                            seatNumber,
                            row[4] == null ? null : row[4].toString(),
                            toBigDecimal(row[5])
                    ));
                    if (section != null && rowNumber != null && seatNumber != null) {
                        seatLabels.add(section + "-" + rowNumber + "-" + seatNumber);
                    }
                }

                items.add(new InternalBookingHistoryDetailResponse(
                        booking.getId(),
                        booking.getStatus(),
                        booking.getConfirmedAt(),
                        booking.getCanceledAt(),
                        concertInfo == null ? null : concertInfo.getConcertTitle(),
                        concertInfo == null ? null : concertInfo.getConcertVenue(),
                        concertInfo == null || concertInfo.getShowTime() == null
                                ? null
                                : OffsetDateTime.ofInstant(concertInfo.getShowTime(), ZoneOffset.UTC),
                        seats.size(),
                        seatLabels,
                        seats
                ));
            });
        }

        return new InternalBookingHistoryDetailsResponse(items);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).longValue();
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        return ((Number) value).intValue();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }
}
