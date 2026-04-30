package com.example.SKALA_Mini_Project_1.modules.seats.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "seats", schema = "concert")
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

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

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

    public void hold(Long userId, int holdDurationMinutes) {
        if (this.status == SeatStatus.RESERVED) {
            throw new IllegalStateException("이미 판매된 좌석입니다.");
        }

        if (this.status == SeatStatus.HOLD && this.heldUntil != null && this.heldUntil.isAfter(LocalDateTime.now())) {
            if (!this.heldBy.equals(userId)) {
                throw new IllegalStateException("이미 다른 사용자에 의해 선점된 좌석입니다.");
            }
        }

        this.status = SeatStatus.HOLD;
        this.heldBy = userId;
        this.heldUntil = LocalDateTime.now().plusMinutes(holdDurationMinutes);
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.heldBy = null;
        this.heldUntil = null;
    }

    public void sell() {
        if (this.status != SeatStatus.HOLD) {
            throw new IllegalStateException("선점되지 않은 좌석은 판매할 수 없습니다.");
        }
        this.status = SeatStatus.RESERVED;
    }

    public boolean isHoldExpired() {
        return this.status == SeatStatus.HOLD && (this.heldUntil == null || this.heldUntil.isBefore(LocalDateTime.now()));
    }

    public Long getId() {
        return id;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public Long getScheduleId() {
        return scheduleId;
    }
}
