package com.example.SKALA_Mini_Project_1.modules.reconciliation.service;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.SKALA_Mini_Project_1.modules.reconciliation.domain.ReconciliationTask;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.repository.ReconciliationTaskRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReconciliationMonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationMonitorScheduler.class);
    private final ReconciliationTaskRepository reconciliationTaskRepository;

    @Scheduled(fixedDelayString = "${ticketing.reconciliation.monitor.fixed-delay-ms:30000}")
    public void logRequestedBacklog() {
        long requested = reconciliationTaskRepository.countByStatus("REQUESTED");
        if (requested == 0) {
            return;
        }

        List<ReconciliationTask> samples = reconciliationTaskRepository.findTop20ByStatusOrderByRequestedAtAsc("REQUESTED");
        String sampleIds = samples.stream()
                .map(task -> task.getId().toString())
                .collect(Collectors.joining(","));

        log.warn(
                "Reconciliation backlog detected. requestedCount={}, sampleTaskIds={}",
                requested,
                sampleIds
        );
    }
}
