package com.example.SKALA_Mini_Project_1.modules.reconciliation.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.SKALA_Mini_Project_1.modules.finalization.service.InternalApiGuard;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.dto.ReconciliationTaskItemResponse;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.dto.ReconciliationTaskSummaryResponse;
import com.example.SKALA_Mini_Project_1.modules.reconciliation.service.ReconciliationTaskService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/ops/reconciliations")
public class InternalReconciliationOpsController {

    private final InternalApiGuard internalApiGuard;
    private final ReconciliationTaskService reconciliationTaskService;

    @GetMapping("/summary")
    public ResponseEntity<ReconciliationTaskSummaryResponse> getSummary(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(reconciliationTaskService.getSummary());
    }

    @GetMapping
    public ResponseEntity<List<ReconciliationTaskItemResponse>> getRecentTasks(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(reconciliationTaskService.getRecentTasks());
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<ReconciliationTaskItemResponse> retryTask(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @PathVariable UUID taskId
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(reconciliationTaskService.retryTask(taskId));
    }
}
