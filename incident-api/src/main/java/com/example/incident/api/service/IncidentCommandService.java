package com.example.incident.api.service;

import com.example.incident.api.domain.Incident;
import com.example.incident.api.domain.IncidentAnalysisVersion;
import com.example.incident.api.domain.IncidentAnalysisVersionRepository;
import com.example.incident.api.domain.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IncidentCommandService {

    private final IncidentRepository incidentRepository;
    private final IncidentAnalysisVersionRepository analysisVersionRepository;

    @Transactional
    public UUID reanalyze(UUID incidentId, String requestedBy) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new NoSuchElementException("Incident not found: " + incidentId);
        }

        int nextVersion = analysisVersionRepository.countByIncidentId(incidentId) + 1;

        IncidentAnalysisVersion version = new IncidentAnalysisVersion();
        version.setAnalysisVersionId(UUID.randomUUID());
        version.setIncidentId(incidentId);
        version.setVersionNumber(nextVersion);
        version.setAnalysisStatus("PENDING");
        version.setTriggerType("MANUAL");
        version.setRequestedBy(requestedBy != null ? requestedBy : "operator");
        version.setInputSchemaVersion("incident-input.v1");
        version.setCreatedAt(OffsetDateTime.now());

        analysisVersionRepository.save(version);

        log.info("[api] Reanalyze requested. incidentId={} version={} by={}", incidentId, nextVersion, requestedBy);
        return version.getAnalysisVersionId();
    }

    @Transactional
    public void acknowledge(UUID incidentId, String operatorId) {
        Incident incident = findIncident(incidentId);
        if (!"ANALYZED".equals(incident.getStatus()) && !"OPEN".equals(incident.getStatus())) {
            throw new IllegalStateException("Cannot acknowledge incident in status: " + incident.getStatus());
        }
        incident.setStatus("ACKNOWLEDGED");
        incident.setUpdatedAt(OffsetDateTime.now());
        incidentRepository.save(incident);
        log.info("[api] Acknowledged. incidentId={} by={}", incidentId, operatorId);
    }

    @Transactional
    public void resolve(UUID incidentId, String operatorId) {
        Incident incident = findIncident(incidentId);
        if ("RESOLVED".equals(incident.getStatus())) {
            throw new IllegalStateException("Incident is already resolved");
        }
        incident.setStatus("RESOLVED");
        incident.setResolvedAt(OffsetDateTime.now());
        incident.setResolvedBy(operatorId);
        incident.setUpdatedAt(OffsetDateTime.now());
        incidentRepository.save(incident);
        log.info("[api] Resolved. incidentId={} by={}", incidentId, operatorId);
    }

    private Incident findIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + incidentId));
    }
}
