package com.example.incident.api.controller;

import com.example.incident.api.dto.AnalysisVersionResponse;
import com.example.incident.api.dto.IncidentDetailResponse;
import com.example.incident.api.dto.IncidentSummaryResponse;
import com.example.incident.api.dto.ReanalyzeRequest;
import com.example.incident.api.dto.StatusTransitionRequest;
import com.example.incident.api.service.IncidentCommandService;
import com.example.incident.api.service.IncidentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ops/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentQueryService queryService;
    private final IncidentCommandService commandService;

    @GetMapping
    public Page<IncidentSummaryResponse> listIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String incidentType,
            @RequestParam(required = false) String severity,
            @PageableDefault(size = 20, sort = "updatedAt") Pageable pageable
    ) {
        return queryService.listIncidents(status, incidentType, severity, pageable);
    }

    @GetMapping("/{incidentId}")
    public IncidentDetailResponse getIncident(@PathVariable UUID incidentId) {
        return queryService.getIncident(incidentId);
    }

    @GetMapping("/{incidentId}/analyses")
    public List<AnalysisVersionResponse> listAnalyses(
            @PathVariable UUID incidentId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return queryService.listAnalyses(incidentId, pageable);
    }

    @PostMapping("/{incidentId}/reanalyze")
    public ResponseEntity<Map<String, Object>> reanalyze(
            @PathVariable UUID incidentId,
            @RequestBody(required = false) ReanalyzeRequest request
    ) {
        String requestedBy = request != null ? request.requestedBy() : null;
        UUID analysisVersionId = commandService.reanalyze(incidentId, requestedBy);
        return ResponseEntity.accepted().body(Map.of(
                "analysisVersionId", analysisVersionId,
                "status", "PENDING"
        ));
    }

    @PostMapping("/{incidentId}/acknowledge")
    public ResponseEntity<Void> acknowledge(
            @PathVariable UUID incidentId,
            @RequestBody(required = false) StatusTransitionRequest request
    ) {
        String operatorId = request != null ? request.operatorId() : "unknown";
        commandService.acknowledge(incidentId, operatorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{incidentId}/resolve")
    public ResponseEntity<Void> resolve(
            @PathVariable UUID incidentId,
            @RequestBody(required = false) StatusTransitionRequest request
    ) {
        String operatorId = request != null ? request.operatorId() : "unknown";
        commandService.resolve(incidentId, operatorId);
        return ResponseEntity.noContent().build();
    }
}
