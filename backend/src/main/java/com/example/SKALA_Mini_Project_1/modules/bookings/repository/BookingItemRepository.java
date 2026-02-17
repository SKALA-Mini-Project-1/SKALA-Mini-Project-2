package com.example.SKALA_Mini_Project_1.modules.bookings.repository;

import com.example.SKALA_Mini_Project_1.modules.bookings.domain.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {
    // payment/create에서 booking_id를 이용해서 seat_id 조회
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
    Long findFirstSeatIdByBookingId(UUID bookingId);

    // booking_id를 이용한 예약 정보 조회 시, 예약의 상세 내역을 화면에 랜더링하기 위한 데이터 조회 쿼리문 (해당 예약에 포함된 좌석 목록(상세)을 조회하기 위한 쿼리)
    // 결제 화면의 “선택좌석” 영역 렌더링과 좌석별 금액/등급 표시에 사용
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
    List<Object[]> findBookedSeatDetails(UUID bookingId);

}
