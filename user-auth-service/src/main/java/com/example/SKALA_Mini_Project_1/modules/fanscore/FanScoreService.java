package com.example.SKALA_Mini_Project_1.modules.fanscore;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FanScoreService {

    public static final int POINTS_PER_CONFIRMED_BOOKING = 1000;

    private final UserArtistFanScoreRepository userArtistFanScoreRepository;

    public int getTotalFanScore(Long userId) {
        Integer total = userArtistFanScoreRepository.sumTotalScoreByUserId(userId);
        return total == null ? 0 : Math.max(0, total);
    }

    public int getArtistFanScore(Long userId, Long artistId) {
        if (userId == null || artistId == null) {
            return 0;
        }

        return userArtistFanScoreRepository.findByUserIdAndArtistId(userId, artistId)
                .map(UserArtistFanScore::getTotalScoreValue)
                .orElse(0);
    }

    @Transactional
    public int applyAttendanceConfirmed(Long userId, Long artistId, UUID bookingId) {
        if (userId == null || artistId == null || bookingId == null) {
            throw new IllegalArgumentException("Fan Score 반영 요청이 올바르지 않습니다.");
        }

        UserArtistFanScore score = userArtistFanScoreRepository.findByUserIdAndArtistId(userId, artistId)
                .orElseGet(() -> UserArtistFanScore.builder()
                        .userId(userId)
                        .artistId(artistId)
                        .build());

        score.addBookingScore(POINTS_PER_CONFIRMED_BOOKING);
        return userArtistFanScoreRepository.save(score).getTotalScoreValue();
    }
}
