package com.example.SKALA_Mini_Project_1.modules.reconciliation.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.SKALA_Mini_Project_1.modules.reconciliation.domain.ReconciliationTask;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.domain.ReconciliationTaskStatus;

public interface ReconciliationTaskRepository extends JpaRepository<ReconciliationTask, UUID> {
    boolean existsByBookingIdAndPaymentIdAndMismatchTypeAndStatusIn(
            UUID bookingId,
            UUID paymentId,
            String mismatchType,
            List<ReconciliationTaskStatus> statuses
    );

    long countByStatus(ReconciliationTaskStatus status);

    long countByStatusIn(List<ReconciliationTaskStatus> statuses);

    List<ReconciliationTask> findTop50ByOrderByRequestedAtDesc();

    List<ReconciliationTask> findTop20ByStatusOrderByRequestedAtAsc(ReconciliationTaskStatus status);

    List<ReconciliationTask> findTop20ByStatusOrderByRequestedAtDesc(ReconciliationTaskStatus status);

    List<ReconciliationTask> findTop50ByStatusOrderByRequestedAtDesc(ReconciliationTaskStatus status);

    List<ReconciliationTask> findTop50ByStatusInOrderByRequestedAtAsc(List<ReconciliationTaskStatus> statuses);
}
