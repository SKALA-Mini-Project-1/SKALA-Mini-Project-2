package com.example.SKALA_Mini_Project_1.modules.bookings.service;

import com.example.SKALA_Mini_Project_1.global.redis.RedisLockRepository;
import com.example.SKALA_Mini_Project_1.modules.bookings.domain.Booking;
import com.example.SKALA_Mini_Project_1.modules.bookings.domain.BookingItem;
import com.example.SKALA_Mini_Project_1.modules.bookings.dto.CreateBookingRequest;
import com.example.SKALA_Mini_Project_1.modules.bookings.dto.CreateBookingResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingItemRepository;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingRepository;
import com.example.SKALA_Mini_Project_1.modules.seats.repository.SeatBookingView;
import com.example.SKALA_Mini_Project_1.modules.seats.repository.SeatRepository;
import com.example.SKALA_Mini_Project_1.modules.users.User;
import com.example.SKALA_Mini_Project_1.modules.users.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final int MAX_SEAT_COUNT = 4;

    private final SeatRepository seatRepository;
    private final RedisLockRepository redisLockRepository;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final UserRepository userRepository;

    @Transactional
    public CreateBookingResponse createBooking(Long userId, CreateBookingRequest request) {
        Set<Long> uniqueSeatIds = new LinkedHashSet<>(request.getSeatIds());
        if (uniqueSeatIds.isEmpty()) {
            throw new IllegalArgumentException("좌석 ID 목록은 비어 있을 수 없습니다.");
        }
        if (uniqueSeatIds.size() > MAX_SEAT_COUNT) {
            throw new IllegalArgumentException("좌석은 최대 4매까지만 선택할 수 있습니다.");
        }

        List<SeatBookingView> seatViews = seatRepository.findSeatBookingViews(request.getConcertId(), new ArrayList<>(uniqueSeatIds));
        if (seatViews.size() != uniqueSeatIds.size()) {
            throw new IllegalArgumentException("유효하지 않은 좌석이 포함되어 있습니다.");
        }

        Set<Long> scheduleIds = new HashSet<>();
        List<Long> notHoldByMeSeatIds = new ArrayList<>();
        List<Long> reservedSeatIds = new ArrayList<>();
        Long minTtlSeconds = null;
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (SeatBookingView seat : seatViews) {
            scheduleIds.add(seat.getScheduleId());
            if ("RESERVED".equals(seat.getStatus())) {
                reservedSeatIds.add(seat.getSeatId());
                continue;
            }

            String owner = redisLockRepository.getSeatOwner(
                    request.getConcertId(),
                    seat.getSeatId()
            );
            Long ttl = redisLockRepository.getSeatLockTtlSeconds(
                    request.getConcertId(),
                    seat.getSeatId()
            );

            if (!Objects.equals(owner, String.valueOf(userId)) || ttl == null) {
                notHoldByMeSeatIds.add(seat.getSeatId());
                continue;
            }

            totalPrice = totalPrice.add(seat.getPrice());
            minTtlSeconds = (minTtlSeconds == null) ? ttl : Math.min(minTtlSeconds, ttl);
        }

        if (!reservedSeatIds.isEmpty()) {
            throw new IllegalStateException("이미 판매된 좌석이 포함되어 있습니다: " + reservedSeatIds);
        }
        if (!notHoldByMeSeatIds.isEmpty()) {
            throw new IllegalStateException("본인이 선점하지 않은 좌석이 포함되어 있습니다: " + notHoldByMeSeatIds);
        }
        if (scheduleIds.size() != 1) {
            throw new IllegalArgumentException("같은 회차(schedule)의 좌석만 예약할 수 있습니다.");
        }
        if (minTtlSeconds == null || minTtlSeconds <= 0) {
            throw new IllegalStateException("좌석 선점이 만료되었습니다. 다시 선택해주세요.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusSeconds(minTtlSeconds);

        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setUserId(userId);
        booking.setScheduleId(scheduleIds.iterator().next());
        booking.setTotalPrice(totalPrice);
        booking.setStatus("HOLDING");
        booking.setCreatedAt(now);
        booking.setExpiresAt(expiresAt);

        Booking saved = bookingRepository.save(booking);

        List<BookingItem> items = uniqueSeatIds.stream().map(seatId -> {
            BookingItem item = new BookingItem();
            item.setBookingId(saved.getId());
            item.setSeatId(seatId);
            return item;
        }).toList();
        bookingItemRepository.saveAll(items);

        return CreateBookingResponse.builder()
                .bookingId(saved.getId())
                .status(saved.getStatus())
                .expiresAt(saved.getExpiresAt())
                .totalPrice(saved.getTotalPrice().longValue())
                .seatIds(new ArrayList<>(uniqueSeatIds))
                .build();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBookingDetail(Long userId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("예약 정보를 찾을 수 없습니다."));

        if (!Objects.equals(booking.getUserId(), userId)) {
            throw new AccessDeniedException("본인 예약만 조회할 수 있습니다.");
        }

        BookingRepository.BookingConcertInfo concertInfo = bookingRepository.findBookingConcertInfo(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("예약 요약 정보를 찾을 수 없습니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자 정보를 찾을 수 없습니다."));

        List<Object[]> seatRows = bookingItemRepository.findBookedSeatDetails(bookingId);
        List<Map<String, Object>> seats = new ArrayList<>();
        List<Long> seatIds = new ArrayList<>();

        for (Object[] row : seatRows) {
            Long seatId = toLong(row[0]);
            seatIds.add(seatId);
            seats.add(Map.of(
                    "seatId", seatId,
                    "section", row[1] == null ? null : row[1].toString(),
                    "rowNumber", toInteger(row[2]),
                    "seatNumber", toInteger(row[3]),
                    "grade", row[4] == null ? null : row[4].toString(),
                    "price", toBigDecimal(row[5])
            ));
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = booking.getExpiresAt();
        long remainingSeconds = 0;
        if (expiresAt != null) {
            remainingSeconds = Math.max(0, ChronoUnit.SECONDS.between(now, expiresAt));
        }
        boolean payable = "HOLDING".equalsIgnoreCase(booking.getStatus()) && remainingSeconds > 0;

        Map<String, Object> booker = new HashMap<>();
        booker.put("name", user.getName());
        booker.put("email", user.getEmail());
        booker.put("phone", user.getPhone());

        Map<String, Object> concert = new HashMap<>();
        concert.put("concertName", concertInfo.getConcertTitle());
        concert.put(
                "showDateTime",
                concertInfo.getShowTime() == null
                        ? null
                        : OffsetDateTime.ofInstant(concertInfo.getShowTime(), ZoneOffset.UTC)
        );
        concert.put("scheduleId", booking.getScheduleId());

        Map<String, Object> response = new HashMap<>();
        response.put("bookingId", booking.getId());
        response.put("status", booking.getStatus());
        response.put("totalPrice", booking.getTotalPrice());
        response.put("expiresAt", expiresAt);
        response.put("remainingSeconds", remainingSeconds);
        response.put("payable", payable);
        response.put("seatIds", seatIds);
        response.put("seats", seats);
        response.put("booker", booker);
        response.put("concert", concert);
        return response;
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
