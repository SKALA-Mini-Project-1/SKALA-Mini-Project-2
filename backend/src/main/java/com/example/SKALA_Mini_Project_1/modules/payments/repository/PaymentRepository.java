// 결제 상태 변경 시 동시성 충돌을 막기 위해 SELECT FOR UPDATE로 조회하는 레포지토리

package com.example.SKALA_Mini_Project_1.modules.payments.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.SKALA_Mini_Project_1.modules.payments.domain.Payment;
import com.example.SKALA_Mini_Project_1.modules.payments.domain.PaymentStatus;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // 결제 상태 변경 시점에만 SELECT FOR UPDATE로 조회한다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.pgOrderId = :orderId")
    Optional<Payment> findByPgOrderIdForUpdate(@Param("orderId") String orderId);

    Optional<Payment> findByBookingId(UUID bookingId);

    Optional<Payment> findByPgOrderId(String pgOrderId);

    long countByStatus(PaymentStatus status);

    List<Payment> findTop50ByStatusOrderByUpdatedAtDesc(PaymentStatus status);
    List<Payment> findTop50ByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Payment> findTop200ByStatusInAndExpiredAtBeforeOrderByExpiredAtAsc(
            Collection<PaymentStatus> statuses,
            OffsetDateTime now
    );
}
