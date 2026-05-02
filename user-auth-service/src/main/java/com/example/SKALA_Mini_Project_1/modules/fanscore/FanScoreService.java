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
    private final FanScoreMetrics fanScoreMetrics;

    public int getTotalFanScore(Long userId) {
        Integer total = userArtistFanScoreRepository.sumTotalScoreByUserId(userId);
        fanScoreMetrics.incrementRead("total", "success");
        return total == null ? 0 : Math.max(0, total);
    }

    public int getArtistFanScore(Long userId, Long artistId) {
        if (userId == null || artistId == null) {
            fanScoreMetrics.incrementRead("artist", "invalid_input");
            return 0;
        }

        return fanScoreMetrics.observe(
                "fairline.fan_score.user_auth.read",
                "fanScoreService#getArtistFanScore",
                "get_artist_fan_score",
                () -> {
                    int score = userArtistFanScoreRepository.findByUserIdAndArtistId(userId, artistId)
                            .map(UserArtistFanScore::getTotalScoreValue)
                            .orElse(0);
                    fanScoreMetrics.incrementRead("artist", score > 0 ? "hit" : "miss");
                    return score;
                }
        );
    }

    @Transactional
    public int applyAttendanceConfirmed(Long userId, Long artistId, UUID bookingId) {
        return fanScoreMetrics.observe(
                "fairline.fan_score.user_auth.apply",
                "fanScoreService#applyAttendanceConfirmed",
                "apply_attendance_confirmed",
                () -> {
                    if (userId == null || artistId == null || bookingId == null) {
                        fanScoreMetrics.incrementApply("invalid_input");
                        throw new IllegalArgumentException("Fan Score 반영 요청이 올바르지 않습니다.");
                    }

                    UserArtistFanScore score = userArtistFanScoreRepository.findByUserIdAndArtistId(userId, artistId)
                            .orElseGet(() -> UserArtistFanScore.builder()
                                    .userId(userId)
                                    .artistId(artistId)
                                    .build());

                    score.addBookingScore(POINTS_PER_CONFIRMED_BOOKING);
                    int totalScore = userArtistFanScoreRepository.save(score).getTotalScoreValue();
                    fanScoreMetrics.incrementApply("applied");
                    fanScoreMetrics.recordTotalScoreAfterApply(totalScore);
                    return totalScore;
                }
        );
    }
}
