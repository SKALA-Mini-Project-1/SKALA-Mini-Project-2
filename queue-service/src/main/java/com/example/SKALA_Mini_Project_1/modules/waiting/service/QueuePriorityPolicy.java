package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import com.example.SKALA_Mini_Project_1.integration.concert.ConcertServiceClient;
import com.example.SKALA_Mini_Project_1.integration.userauth.UserAuthClient;
import com.example.SKALA_Mini_Project_1.modules.waiting.exception.DownstreamServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueuePriorityPolicy {

    private final ConcertServiceClient concertServiceClient;
    private final UserAuthClient userAuthClient;

    public long resolveBoostMillis(Long userId, Long concertId) {
        if (userId == null || concertId == null) {
            return 0L;
        }

        try {
            Long artistId = concertServiceClient.getArtistIdForConcert(concertId);
            if (artistId == null) {
                return 0L;
            }

            return Math.max(0L, userAuthClient.getArtistFanScore(userId, artistId));
        } catch (IllegalArgumentException | DownstreamServiceException e) {
            // Fan score 조회 실패 시에도 대기열 기능은 중립 우선순위로 유지한다.
            return 0L;
        }
    }
}
