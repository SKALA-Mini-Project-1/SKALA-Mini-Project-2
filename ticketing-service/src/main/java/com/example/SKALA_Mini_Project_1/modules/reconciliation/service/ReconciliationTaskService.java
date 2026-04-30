package com.example.SKALA_Mini_Project_1.modules.reconciliation.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.SKALA_Mini_Project_1.modules.finalization.dto.InternalBookingFinalizationResponse;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.domain.ReconciliationTask;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.domain.ReconciliationTaskStatus;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.dto.ReconciliationTaskItemResponse;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.dto.ReconciliationTaskSummaryResponse;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.repository.ReconciliationTaskRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class ReconciliationTaskService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationTaskService.class);
    private static final List<ReconciliationTaskStatus> ACTIVE_STATUSES = List.of(
            ReconciliationTaskStatus.REQUESTED,
            ReconciliationTaskStatus.RUNNING,
            ReconciliationTaskStatus.RETRY_WAIT,
            ReconciliationTaskStatus.WAITING_MANUAL_APPROVAL
    );
    private final ReconciliationTaskRepository reconciliationTaskRepository;
    @Value("${ticketing.reconciliation.retry.max-auto-retry-count:3}")
    private int maxAutoRetryCount;
    @Value("${ticketing.reconciliation.retry.wait-ms:60000}")
    private long retryWaitMs;

    @Transactional
    public void recordFinalizationMismatchIfNeeded(InternalBookingFinalizationResponse response) {
        if (response == null || !isMismatchOutcome(response.outcome())) {
            return;
        }

        if (reconciliationTaskRepository.existsByBookingIdAndPaymentIdAndMismatchTypeAndStatusIn(
                response.bookingId(),
                response.paymentId(),
                response.outcome(),
                ACTIVE_STATUSES
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
        task.setStatus(ReconciliationTaskStatus.REQUESTED);
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
        task.setLastProcessedAt(null);
        task.setNextRetryAt(null);
        reconciliationTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public long countRequestedTasks() {
        return reconciliationTaskRepository.countByStatus(ReconciliationTaskStatus.REQUESTED);
    }

    @Transactional(readOnly = true)
    public ReconciliationTaskSummaryResponse getSummary() {
        long waitingManualCount = reconciliationTaskRepository.countByStatus(ReconciliationTaskStatus.WAITING_MANUAL_APPROVAL);
        return new ReconciliationTaskSummaryResponse(
                reconciliationTaskRepository.countByStatus(ReconciliationTaskStatus.REQUESTED),
                reconciliationTaskRepository.countByStatus(ReconciliationTaskStatus.RUNNING),
                reconciliationTaskRepository.countByStatus(ReconciliationTaskStatus.RETRY_WAIT),
                waitingManualCount,
                reconciliationTaskRepository.countByStatus(ReconciliationTaskStatus.COMPLETED),
                reconciliationTaskRepository.countByStatus(ReconciliationTaskStatus.FAILED_PERMANENT)
        );
    }

    @Transactional(readOnly = true)
    public java.util.List<ReconciliationTaskItemResponse> getRecentTasks() {
        return reconciliationTaskRepository.findTop50ByOrderByRequestedAtDesc()
                .stream()
                .map(this::toItemResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<ReconciliationTaskItemResponse> getFailedTasks() {
        return reconciliationTaskRepository.findTop50ByStatusInOrderByRequestedAtAsc(
                        List.of(
                                ReconciliationTaskStatus.WAITING_MANUAL_APPROVAL,
                                ReconciliationTaskStatus.FAILED_PERMANENT
                        )
                )
                .stream()
                .sorted((left, right) -> right.getRequestedAt().compareTo(left.getRequestedAt()))
                .map(this::toItemResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReconciliationTaskItemResponse getTask(UUID taskId) {
        ReconciliationTask task = reconciliationTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation task not found: " + taskId));
        return toItemResponse(task);
    }

    @Transactional
    public void processRequestedTasks() {
        List<ReconciliationTask> tasks = reconciliationTaskRepository.findTop50ByStatusInOrderByRequestedAtAsc(
                Arrays.asList(ReconciliationTaskStatus.REQUESTED, ReconciliationTaskStatus.RETRY_WAIT)
        );
        if (tasks.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (ReconciliationTask task : tasks) {
            if (!isDue(task, now)) {
                continue;
            }
            processOneTask(task, now);
        }
    }

    @Transactional
    public ReconciliationTaskItemResponse retryTask(UUID taskId) {
        ReconciliationTask task = reconciliationTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Reconciliation task not found: " + taskId));

        if (ReconciliationTaskStatus.COMPLETED == task.getStatus()
                || ReconciliationTaskStatus.REQUESTED == task.getStatus()
                || ReconciliationTaskStatus.RUNNING == task.getStatus()) {
            return toItemResponse(task);
        }

        task.setStatus(ReconciliationTaskStatus.REQUESTED);
        task.setCompletedAt(null);
        task.setNextRetryAt(null);
        task.setLastError(null);
        return toItemResponse(task);
    }

    private void processOneTask(ReconciliationTask task, OffsetDateTime now) {
        int nextRetryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        task.setStatus(ReconciliationTaskStatus.RUNNING);
        task.setLastProcessedAt(now);
        task.setRetryCount(nextRetryCount);

        try {
            if ("BOOKING_ALREADY_CONFIRMED".equalsIgnoreCase(task.getMismatchType())) {
                task.setStatus(ReconciliationTaskStatus.COMPLETED);
                task.setCompletedAt(now);
                task.setNextRetryAt(null);
                task.setLastError(null);
                return;
            }

            if (nextRetryCount < maxAutoRetryCount) {
                task.setStatus(ReconciliationTaskStatus.RETRY_WAIT);
                task.setNextRetryAt(now.plusNanos(retryWaitMs * 1_000_000));
                task.setLastError("Retry scheduled for mismatch: " + task.getMismatchType());
                return;
            }

            task.setStatus(ReconciliationTaskStatus.WAITING_MANUAL_APPROVAL);
            task.setNextRetryAt(null);
            task.setLastError("Manual follow-up required for mismatch: " + task.getMismatchType());
        } catch (Exception e) {
            log.error("Failed to process reconciliation task. taskId={}", task.getId(), e);
            task.setLastError(e.getMessage());

            if (nextRetryCount >= maxAutoRetryCount) {
                task.setStatus(ReconciliationTaskStatus.FAILED_PERMANENT);
                task.setNextRetryAt(null);
                task.setCompletedAt(now);
                return;
            }

            task.setStatus(ReconciliationTaskStatus.RETRY_WAIT);
            task.setNextRetryAt(now.plusNanos(retryWaitMs * 1_000_000));
        }
    }

    private boolean isDue(ReconciliationTask task, OffsetDateTime now) {
        if (task.getStatus() == ReconciliationTaskStatus.REQUESTED) {
            return true;
        }

        return task.getStatus() == ReconciliationTaskStatus.RETRY_WAIT
                && (task.getNextRetryAt() == null || !task.getNextRetryAt().isAfter(now));
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
                task.getStatus().name(),
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
                task.getLastProcessedAt(),
                task.getNextRetryAt(),
                task.getCompletedAt(),
                task.getRetryCount(),
                task.getLastError()
        );
    }
}
