package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import com.example.SKALA_Mini_Project_1.integration.concert.ConcertServiceClient;
import com.example.SKALA_Mini_Project_1.integration.userauth.UserAuthClient;
import com.example.SKALA_Mini_Project_1.modules.waiting.observability.QueueMetrics;
import com.example.SKALA_Mini_Project_1.modules.waiting.exception.DownstreamServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueuePriorityPolicy {

    private final ConcertServiceClient concertServiceClient;
    private final UserAuthClient userAuthClient;
    private final QueueMetrics queueMetrics;

    public long resolveBoostMillis(Long userId, Long concertId) {
        return queueMetrics.observe(
                "fairline.queue.fan_score.resolve",
                "queuePriorityPolicy#resolveBoostMillis",
                "resolve_boost",
                () -> {
                    if (userId == null || concertId == null) {
                        queueMetrics.recordFanScoreLookup("invalid_input", 0L);
                        return 0L;
                    }

                    try {
                        Long artistId = concertServiceClient.getArtistIdForConcert(concertId);
                        if (artistId == null) {
                            queueMetrics.recordFanScoreLookup("artist_missing", 0L);
                            return 0L;
                        }

                        long boostMillis = Math.max(0L, userAuthClient.getArtistFanScore(userId, artistId));
                        queueMetrics.recordFanScoreLookup("success", boostMillis);
                        return boostMillis;
                    } catch (IllegalArgumentException | DownstreamServiceException e) {
                        // Fan score 조회 실패 시에도 대기열 기능은 중립 우선순위로 유지한다.
                        queueMetrics.recordFanScoreLookup("fallback", 0L);
                        return 0L;
                    }
                }
        );
    }
}
