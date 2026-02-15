package com.example.SKALA_Mini_Project_1.modules.seats.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String section;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    private Long heldBy;

    private LocalDateTime heldUntil;

    @Version
    private Long version;

    public Seat(String section, Integer rowNumber, Integer seatNumber) {
        this.section = section;
        this.rowNumber = rowNumber;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    /**
     * 좌석 선점 로직
     * @param userId 선점하려는 사용자 ID
     * @param holdDurationMinutes 선점 유지 시간(분)
     */
    public void hold(Long userId, int holdDurationMinutes) {
        if (this.status == SeatStatus.RESERVED) {
            throw new IllegalStateException("이미 판매된 좌석입니다.");
        }
        
        // 이미 선점된 경우, 만료 시간이 지났는지 확인
        if (this.status == SeatStatus.HOLD && this.heldUntil != null && this.heldUntil.isAfter(LocalDateTime.now())) {
            if (!this.heldBy.equals(userId)) {
                throw new IllegalStateException("이미 다른 사용자에 의해 선점된 좌석입니다.");
            }
        }

        this.status = SeatStatus.HOLD;
        this.heldBy = userId;
        this.heldUntil = LocalDateTime.now().plusMinutes(holdDurationMinutes);
    }

    /**
     * 선점 해제 (예약 취소 또는 시간 만료 시)
     */
    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.heldBy = null;
        this.heldUntil = null;
    }

    /**
     * 최종 판매 완료
     */
    public void sell() {
        if (this.status != SeatStatus.HOLD) {
            throw new IllegalStateException("선점되지 않은 좌석은 판매할 수 없습니다.");
        }
        this.status = SeatStatus.RESERVED;
    }

    /**
     * 선점 만료 여부 확인
     */
    public boolean isHoldExpired() {
        return this.status == SeatStatus.HOLD && (this.heldUntil == null || this.heldUntil.isBefore(LocalDateTime.now()));
    }
}
