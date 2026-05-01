package com.example.incident.detector.incident;

import com.example.incident.detector.incident.dto.IncidentCreateCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentWriteService {

    private final IncidentRepository incidentRepository;
    private final IncidentStatusHistoryRepository historyRepository;
    private final IncidentAnalysisVersionRepository analysisVersionRepository;

    @Value("${detector.severity.escalation-count:10}")
    private int escalationCount;

    @Value("${detector.severity.escalation-minutes:20}")
    private int escalationMinutes;

    /**
     * incident 생성 또는 기존 OPEN incident 갱신.
     * 동일 (type, key) 쌍의 OPEN incident가 이미 있으면 last_detected_at만 업데이트한다.
     */
    @Transactional
    public Incident createOrUpdate(IncidentCreateCommand cmd) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return incidentRepository
                .findOpenByTypeAndKey(cmd.incidentType(), cmd.incidentKey())
                .map(existing -> update(existing, cmd, now))
                .orElseGet(() -> create(cmd, now));
    }

    private Incident create(IncidentCreateCommand cmd, OffsetDateTime now) {
        String severity = applySeverityEscalation(cmd.incidentType(), cmd.severity(), now);

        Incident incident = new Incident();
        incident.setIncidentId(UUID.randomUUID());
        incident.setIncidentType(cmd.incidentType());
        incident.setIncidentKey(cmd.incidentKey());
        incident.setStatus("OPEN");
        incident.setSeverity(severity);
        incident.setPrimaryPaymentId(cmd.primaryPaymentId());
        incident.setPrimaryBookingId(cmd.primaryBookingId());
        incident.setUserId(cmd.userId());
        incident.setConcertId(cmd.concertId());
        incident.setScheduleId(cmd.scheduleId());
        incident.setFirstDetectedAt(now);
        incident.setLastDetectedAt(now);
        incident.setOpenReasonSignal(cmd.signal());
        incident.setCurrentStateJsonb(cmd.currentStateJson());
        incident.setNeedsHumanApproval("critical".equals(severity) || "high".equals(severity));
        incident.setCreatedAt(now);
        incident.setUpdatedAt(now);

        incidentRepository.save(incident);
        recordStatusHistory(incident.getIncidentId(), null, "OPEN", "detector", cmd.signal());
        requestAnalysis(incident, "NEW_INCIDENT");

        log.info("[detector] Incident created. type={}, key={}, severity={}, signal={}",
                cmd.incidentType(), cmd.incidentKey(), severity, cmd.signal());
        return incident;
    }

    private Incident update(Incident existing, IncidentCreateCommand cmd, OffsetDateTime now) {
        existing.setLastDetectedAt(now);
        existing.setCurrentStateJsonb(cmd.currentStateJson());
        existing.setUpdatedAt(now);

        String escalated = applySeverityEscalation(cmd.incidentType(), existing.getSeverity(), existing.getFirstDetectedAt());
        if (!escalated.equals(existing.getSeverity())) {
            existing.setSeverity(escalated);
            log.info("[detector] Incident severity escalated. incidentId={}, newSeverity={}",
                    existing.getIncidentId(), escalated);
        }

        incidentRepository.save(existing);
        requestAnalysis(existing, "STATE_CHANGED");

        log.info("[detector] Incident updated. incidentId={}, type={}", existing.getIncidentId(), existing.getIncidentType());
        return existing;
    }

    private String applySeverityEscalation(String incidentType, String currentSeverity, OffsetDateTime firstDetectedAt) {
        long openCount = incidentRepository.countOpenByType(incidentType);
        if (openCount >= escalationCount) {
            return escalate(currentSeverity);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (firstDetectedAt != null &&
                firstDetectedAt.plusMinutes(escalationMinutes).isBefore(now)) {
            return escalate(currentSeverity);
        }

        return currentSeverity;
    }

    private String escalate(String severity) {
        return switch (severity) {
            case "low"    -> "medium";
            case "medium" -> "high";
            case "high"   -> "critical";
            default       -> severity;
        };
    }

    private void recordStatusHistory(UUID incidentId, String fromStatus, String toStatus,
                                     String changedBy, String reason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        IncidentStatusHistory history = new IncidentStatusHistory();
        history.setHistoryId(UUID.randomUUID());
        history.setIncidentId(incidentId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(changedBy);
        history.setChangeReason(reason);
        history.setCreatedAt(now);
        historyRepository.save(history);
    }

    private void requestAnalysis(Incident incident, String triggerType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int nextVersion = incident.getLatestAnalysisVersion() + 1;

        IncidentAnalysisVersion version = new IncidentAnalysisVersion();
        version.setAnalysisVersionId(UUID.randomUUID());
        version.setIncidentId(incident.getIncidentId());
        version.setVersionNumber(nextVersion);
        version.setAnalysisStatus("PENDING");
        version.setInputSchemaVersion("incident-analysis-input.v1");
        version.setOutputSchemaVersion("incident-analysis-output.v1");
        version.setTriggerType(triggerType);
        version.setRequestedBy("detector");
        version.setCreatedAt(now);
        analysisVersionRepository.save(version);

        incident.setLatestAnalysisVersion(nextVersion);
        incident.setStatus("OPEN");
    }
}
