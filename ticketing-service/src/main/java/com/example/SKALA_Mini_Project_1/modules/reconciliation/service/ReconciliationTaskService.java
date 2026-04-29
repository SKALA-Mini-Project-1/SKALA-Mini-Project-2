package com.example.SKALA_Mini_Project_1.modules.reconciliation.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingFinalizationResponse;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.domain.ReconciliationTask;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.dto.ReconciliationTaskItemResponse;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.dto.ReconciliationTaskSummaryResponse;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.repository.ReconciliationTaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReconciliationTaskService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationTaskService.class);
    private static final String STATUS_REQUESTED = "REQUESTED";
    private static final String STATUS_WAITING_MANUAL = "WAITING_MANUAL_APPROVAL";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private final ReconciliationTaskRepository reconciliationTaskRepository;

    @Transactional
    public void recordFinalizationMismatchIfNeeded(InternalBookingFinalizationResponse response) {
        if (response == null || !isMismatchOutcome(response.outcome())) {
            return;
        }

        if (reconciliationTaskRepository.existsByBookingIdAndPaymentIdAndMismatchTypeAndStatus(
                response.bookingId(),
                response.paymentId(),
                response.outcome(),
                STATUS_REQUESTED
        )) {
            log.info(
                    "Skip duplicate reconciliation task. bookingId={}, paymentId={}, mismatchType={}",
                    response.bookingId(),
                    response.paymentId(),
                    response.outcome()
            );
            return;
        }

        ReconciliationTask task = new ReconciliationTask();
        task.setId(UUID.randomUUID());
        task.setBookingId(response.bookingId());
        task.setPaymentId(response.paymentId());
        task.setMismatchType(response.outcome());
        task.setStatus(STATUS_REQUESTED);
        task.setBookingStatus(response.bookingStatus());
        task.setReasonCode(response.reasonCode());
        task.setUserId(response.userId());
        task.setConcertId(response.concertId());
        task.setScheduleId(response.scheduleId());
        task.setSeatIdsCsv(response.seatIds() == null
                ? null
                : response.seatIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
        task.setPgOrderId(response.pgOrderId());
        task.setPgPaymentKey(response.pgPaymentKey());
        task.setAmount(response.amount());
        task.setRequestedAt(response.processedAt());
        task.setRetryCount(0);
        reconciliationTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public long countRequestedTasks() {
        return reconciliationTaskRepository.countByStatus(STATUS_REQUESTED);
    }

    @Transactional(readOnly = true)
    public ReconciliationTaskSummaryResponse getSummary() {
        return new ReconciliationTaskSummaryResponse(
                reconciliationTaskRepository.countByStatus(STATUS_REQUESTED),
                reconciliationTaskRepository.countByStatus(STATUS_WAITING_MANUAL),
                reconciliationTaskRepository.countByStatus(STATUS_COMPLETED)
        );
    }

    @Transactional(readOnly = true)
    public java.util.List<ReconciliationTaskItemResponse> getRecentTasks() {
        return reconciliationTaskRepository.findTop50ByOrderByRequestedAtDesc()
                .stream()
                .map(this::toItemResponse)
                .toList();
    }

    @Transactional
    public void processRequestedTasks() {
        List<ReconciliationTask> tasks = reconciliationTaskRepository.findTop20ByStatusOrderByRequestedAtDesc(STATUS_REQUESTED);
        if (tasks.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (ReconciliationTask task : tasks) {
            Integer currentRetryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
            task.setRetryCount(currentRetryCount + 1);

            if ("BOOKING_ALREADY_CONFIRMED".equalsIgnoreCase(task.getMismatchType())) {
                task.setStatus(STATUS_COMPLETED);
                task.setCompletedAt(now);
                task.setLastError(null);
                continue;
            }

            task.setStatus(STATUS_WAITING_MANUAL);
            task.setLastError("Manual follow-up required for mismatch: " + task.getMismatchType());
        }
    }

    @Transactional
    public ReconciliationTaskItemResponse retryTask(UUID taskId) {
        ReconciliationTask task = reconciliationTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation task not found: " + taskId));

        if (STATUS_COMPLETED.equalsIgnoreCase(task.getStatus())
                || STATUS_REQUESTED.equalsIgnoreCase(task.getStatus())) {
            return toItemResponse(task);
        }

        task.setStatus(STATUS_REQUESTED);
        task.setLastError(null);
        return toItemResponse(task);
    }

    private boolean isMismatchOutcome(String outcome) {
        return "INVALID_HOLD".equalsIgnoreCase(outcome)
                || "BOOKING_ALREADY_CANCELED".equalsIgnoreCase(outcome)
                || "BOOKING_ALREADY_CONFIRMED".equalsIgnoreCase(outcome);
    }

    private ReconciliationTaskItemResponse toItemResponse(ReconciliationTask task) {
        return new ReconciliationTaskItemResponse(
                task.getId(),
                task.getBookingId(),
                task.getPaymentId(),
                task.getMismatchType(),
                task.getStatus(),
                task.getBookingStatus(),
                task.getReasonCode(),
                task.getUserId(),
                task.getConcertId(),
                task.getScheduleId(),
                task.getSeatIdsCsv(),
                task.getPgOrderId(),
                task.getPgPaymentKey(),
                task.getAmount(),
                task.getRequestedAt(),
                task.getRetryCount(),
                task.getLastError()
        );
    }
}
