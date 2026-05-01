package com.example.incident.detector.zombie;

import com.example.incident.detector.incident.IncidentWriteService;
import com.example.incident.detector.incident.dto.IncidentCreateCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * 유예 기간이 경과한 ZombieCandidate를 주기적으로 스캔.
 * Redis에서 hold key가 아직 살아있으면 ZOMBIE_HOLD incident를 생성한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ZombieHoldCheckScheduler {

    private final ZombieHoldCandidateRepository candidateRepository;
    private final ZombieHoldChecker zombieHoldChecker;
    private final IncidentWriteService incidentWriteService;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${detector.zombie-check.fixed-delay-ms:30000}")
    @Transactional
    public void checkCandidates() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<ZombieCandidate> candidates = candidateRepository.findByCheckedFalseAndCheckAfterAtBefore(now);

        if (candidates.isEmpty()) return;

        log.info("[zombie-check] Scanning {} zombie candidates", candidates.size());

        for (ZombieCandidate candidate : candidates) {
            try {
                if (zombieHoldChecker.isHoldStillPresent(candidate)) {
                    raiseIncident(candidate);
                    log.info("[zombie-check] ZOMBIE_HOLD incident triggered. bookingId={}", candidate.getBookingId());
                } else {
                    log.info("[zombie-check] Hold already released. bookingId={}", candidate.getBookingId());
                }
            } catch (Exception e) {
                log.error("[zombie-check] Error checking candidate. bookingId={}", candidate.getBookingId(), e);
            } finally {
                candidate.setChecked(true);
                candidateRepository.save(candidate);
            }
        }
    }

    private void raiseIncident(ZombieCandidate candidate) {
        IncidentCreateCommand cmd = new IncidentCreateCommand(
                "ZOMBIE_HOLD",
                candidate.getBookingId().toString(),
                "medium",
                "ZOMBIE_HOLD_REDIS_KEY_FOUND",
                buildStateJson(candidate),
                null,
                candidate.getBookingId(),
                candidate.getUserId(),
                candidate.getConcertId(),
                candidate.getScheduleId()
        );
        incidentWriteService.createOrUpdate(cmd);
    }

    private String buildStateJson(ZombieCandidate candidate) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "bookingId", candidate.getBookingId().toString(),
                    "endedEventType", orEmpty(candidate.getEndedEventType()),
                    "endedAt", candidate.getEndedAt().toString(),
                    "checkAfterAt", candidate.getCheckAfterAt().toString()
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String orEmpty(String v) {
        return v != null ? v : "";
    }
}
