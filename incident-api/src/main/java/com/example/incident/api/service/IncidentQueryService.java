package com.example.incident.api.service;

import com.example.incident.api.domain.Incident;
import com.example.incident.api.domain.IncidentAnalysisVersion;
import com.example.incident.api.domain.IncidentAnalysisVersionRepository;
import com.example.incident.api.domain.IncidentRepository;
import com.example.incident.api.dto.AnalysisVersionResponse;
import com.example.incident.api.dto.IncidentDetailResponse;
import com.example.incident.api.dto.IncidentSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class IncidentQueryService {

    private final IncidentRepository incidentRepository;
    private final IncidentAnalysisVersionRepository analysisVersionRepository;

    public Page<IncidentSummaryResponse> listIncidents(String status, String incidentType, String severity, Pageable pageable) {
        Page<Incident> page;

        if (incidentType != null && status != null) {
            page = incidentRepository.findByIncidentTypeAndStatus(incidentType, status, pageable);
        } else if (status != null) {
            page = incidentRepository.findByStatus(status, pageable);
        } else if (severity != null) {
            page = incidentRepository.findBySeverity(severity, pageable);
        } else {
            page = incidentRepository.findAll(pageable);
        }

        List<IncidentSummaryResponse> content = page.getContent().stream()
                .map(incident -> {
                    var latest = analysisVersionRepository.findLatestByIncidentId(incident.getIncidentId());
                    String summary = latest.map(IncidentAnalysisVersion::getSummaryText).orElse(null);
                    String analysisStatus = latest.map(IncidentAnalysisVersion::getAnalysisStatus).orElse(null);
                    return IncidentSummaryResponse.from(incident, summary, analysisStatus);
                })
                .toList();

        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    public IncidentDetailResponse getIncident(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NoSuchElementException("Incident not found: " + incidentId));

        List<AnalysisVersionResponse> versions = analysisVersionRepository
                .findByIncidentIdOrderByVersionDesc(incidentId, Pageable.ofSize(20))
                .stream()
                .map(AnalysisVersionResponse::from)
                .toList();

        AnalysisVersionResponse latest = versions.isEmpty() ? null : versions.get(0);
        return IncidentDetailResponse.from(incident, latest, versions);
    }

    public List<AnalysisVersionResponse> listAnalyses(UUID incidentId, Pageable pageable) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new NoSuchElementException("Incident not found: " + incidentId);
        }
        return analysisVersionRepository
                .findByIncidentIdOrderByVersionDesc(incidentId, pageable)
                .stream()
                .map(AnalysisVersionResponse::from)
                .toList();
    }
}
