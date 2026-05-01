package com.example.incident.detector.scheduler;

import com.example.incident.detector.correlation.PendingCorrelation;
import com.example.incident.detector.correlation.PendingCorrelationRepository;
import com.example.incident.detector.rules.GhostOrderRule;
import com.example.incident.detector.rules.UnconfirmedPaymentRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * deadline이 지난 미해결 PendingCorrelation을 주기적으로 스캔하여 incident로 전환.
 * - WAITING_BOOKING_CONFIRM → GHOST_ORDER incident
 * - WAITING_PAYMENT_CONFIRM → UNCONFIRMED_PAYMENT incident
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingCorrelationCheckScheduler {

    private final PendingCorrelationRepository correlationRepository;
    private final GhostOrderRule ghostOrderRule;
    private final UnconfirmedPaymentRule unconfirmedPaymentRule;

    @Scheduled(fixedDelayString = "${detector.pending-check.fixed-delay-ms:30000}")
    @Transactional
    public void checkExpired() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<PendingCorrelation> expired = correlationRepository.findByResolvedFalseAndDeadlineAtBefore(now);

        if (expired.isEmpty()) return;

        log.info("[pending-check] Scanning {} expired correlations", expired.size());

        for (PendingCorrelation pending : expired) {
            try {
                raiseIncident(pending);
                pending.setResolved(true);
                correlationRepository.save(pending);
            } catch (Exception e) {
                log.error("[pending-check] Failed to raise incident. correlationId={}, type={}",
                        pending.getId(), pending.getCorrelationType(), e);
            }
        }
    }

    private void raiseIncident(PendingCorrelation pending) {
        switch (pending.getCorrelationType()) {
            case GhostOrderRule.CORRELATION_TYPE ->
                    ghostOrderRule.raiseIncidentFromExpired(pending);
            case UnconfirmedPaymentRule.CORRELATION_TYPE ->
                    unconfirmedPaymentRule.raiseIncidentFromExpired(pending);
            default ->
                    log.warn("[pending-check] Unknown correlation type: {}", pending.getCorrelationType());
        }
    }
}
