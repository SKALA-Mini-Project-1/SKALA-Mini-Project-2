package com.example.incident.agent.scheduler;

import com.example.incident.agent.analysis.AnalysisService;
import com.example.incident.agent.domain.IncidentAnalysisVersion;
import com.example.incident.agent.domain.IncidentAnalysisVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AnalysisJobScheduler {

    private final IncidentAnalysisVersionRepository versionRepository;
    private final AnalysisService analysisService;

    @Value("${agent.poll.batch-size:5}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${agent.poll.fixed-delay-ms:10000}")
    public void pollAndAnalyze() {
        List<IncidentAnalysisVersion> pending =
                versionRepository.findByAnalysisStatus("PENDING", Pageable.ofSize(batchSize));

        if (pending.isEmpty()) {
            return;
        }

        log.debug("[scheduler] Found {} PENDING analysis versions", pending.size());

        for (IncidentAnalysisVersion version : pending) {
            if (versionRepository.existsByIncidentIdAndAnalysisStatus(version.getIncidentId(), "RUNNING")) {
                log.debug("[scheduler] Skipping incidentId={} — already RUNNING", version.getIncidentId());
                continue;
            }
            try {
                analysisService.analyze(version);
            } catch (Exception e) {
                log.error("[scheduler] Unexpected error analyzing versionId={}: {}",
                        version.getAnalysisVersionId(), e.getMessage(), e);
            }
        }
    }
}
