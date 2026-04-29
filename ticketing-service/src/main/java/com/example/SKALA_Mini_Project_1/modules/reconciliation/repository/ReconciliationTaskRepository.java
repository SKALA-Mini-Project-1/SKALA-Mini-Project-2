package com.example.SKALA_Mini_Project_1.modules.reconciliation.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.SKALA_Mini_Project_1.modules.reconciliation.domain.ReconciliationTask;

public interface ReconciliationTaskRepository extends JpaRepository<ReconciliationTask, UUID> {
    boolean existsByBookingIdAndPaymentIdAndMismatchTypeAndStatus(
            UUID bookingId,
            UUID paymentId,
            String mismatchType,
            String status
    );

    long countByStatus(String status);

    List<ReconciliationTask> findTop20ByStatusOrderByRequestedAtAsc(String status);

    List<ReconciliationTask> findTop50ByOrderByRequestedAtDesc();

    List<ReconciliationTask> findTop20ByStatusOrderByRequestedAtDesc(String status);
}
