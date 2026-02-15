// 결제 상태 변경 시 동시성 충돌을 막기 위해 SELECT FOR UPDATE로 조회하는 레포지토리

package com.example.SKALA_Mini_Project_1.modules.payments.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.SKALA_Mini_Project_1.modules.payments.domain.Payment;

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
}
