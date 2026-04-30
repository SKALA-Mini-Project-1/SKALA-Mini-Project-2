package com.example.SKALA_Mini_Project_1.modules.reconciliation.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReconciliationReplayScheduler {

    private final ReconciliationTaskService reconciliationTaskService;

    @Scheduled(fixedDelayString = "${ticketing.reconciliation.replay.fixed-delay-ms:45000}")
    public void processRequestedTasks() {
        reconciliationTaskService.processRequestedTasks();
    }
}
