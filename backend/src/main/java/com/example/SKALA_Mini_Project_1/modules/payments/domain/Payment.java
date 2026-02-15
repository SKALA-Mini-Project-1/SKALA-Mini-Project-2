package com.example.SKALA_Mini_Project_1.modules.payments.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

    @Id
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "seat_id")
    private Long seatId;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "booking_id", columnDefinition = "uuid", nullable = false)
    private UUID bookingId;

    // 결제 당시 PG로 보낸 상품명 저장용
    @Column(name = "order_name")
    private String orderName;

    @Column(name = "pg_provider")
    private String pgProvider;

    @Column(name = "pg_order_id")
    private String pgOrderId;

        @Column(name = "pg_payment_key")
        private String pgPaymentKey;

        @Column(name = "pg_status")
        private String pgStatus;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

        @Column(name = "completed_at")
        private OffsetDateTime completedAt;

        // ✅ 허용된 상태 전이만 정의하는 전이 맵 (엔티티로 이동)
    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITION_MAP =
            new EnumMap<>(PaymentStatus.class);

    static {
        TRANSITION_MAP.put(PaymentStatus.PENDING,
                Set.of(PaymentStatus.PAYING, PaymentStatus.CANCELED, PaymentStatus.EXPIRED));

        TRANSITION_MAP.put(PaymentStatus.PAYING,
                Set.of(PaymentStatus.PAID, PaymentStatus.FAILED, PaymentStatus.CANCELED, PaymentStatus.EXPIRED));

        TRANSITION_MAP.put(PaymentStatus.PAID,
                Set.of(PaymentStatus.CONFIRMED));

        TRANSITION_MAP.put(PaymentStatus.EXPIRED,
                Set.of(PaymentStatus.REFUND_REQUIRED));
    }

    // ✅ 현재 상태에서 다음 상태로 전이가 가능한지 확인한다 (엔티티 내부)
    private boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return from != null
                && to != null
                && TRANSITION_MAP.containsKey(from)
                && TRANSITION_MAP.get(from).contains(to);
    }

    // ✅ 전이표 기반 상태 변경 (정석: 엔티티가 규칙을 가진다)
    public void changeStatus(PaymentStatus newStatus) {
        PaymentStatus currentStatus = this.status;

        if (!canTransition(currentStatus, newStatus)) {
            throw new IllegalStateException(
                    "Invalid status transition: " + currentStatus + " → " + newStatus
            );
        }

        this.status = newStatus;
    }
}
