package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import org.springframework.stereotype.Service;

import com.example.SKALA_Mini_Project_1.integration.concert.ConcertServiceClient;
import com.example.SKALA_Mini_Project_1.modules.fanscore.UserArtistFanScoreRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueuePriorityService {

    public static final int MAX_QUEUE_PRIORITY_BOOST_MILLIS = 5000;

    private final ConcertServiceClient concertServiceClient;
    private final UserArtistFanScoreRepository userArtistFanScoreRepository;

    public int getQueuePriorityBoostMillis(Long userId, Long concertId) {
        if (userId == null || concertId == null) {
            return 0;
        }

        Long artistId = concertServiceClient.getArtistIdForConcert(concertId);
        if (artistId == null) {
            return 0;
        }

        int artistFanScore = userArtistFanScoreRepository.findByUserIdAndArtistId(userId, artistId)
                .map(score -> score.getTotalScoreValue())
                .orElse(0);

        return Math.min(Math.max(artistFanScore, 0), MAX_QUEUE_PRIORITY_BOOST_MILLIS);
    }
}
