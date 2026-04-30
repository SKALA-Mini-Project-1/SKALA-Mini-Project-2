package com.example.SKALA_Mini_Project_1.modules.fanscore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FanScoreService {

    public static final int POINTS_PER_CONFIRMED_BOOKING = 1000;

    private final UserArtistFanScoreRepository userArtistFanScoreRepository;
    private final FanScoreQueryRepository fanScoreQueryRepository;

    @Transactional
    public void applyConfirmedBookingScore(UUID bookingId, Long userId) {
        if (bookingId == null || userId == null) {
            return;
        }

        Long artistId = fanScoreQueryRepository.findArtistIdByBookingId(bookingId).orElse(null);
        if (artistId == null) {
            return;
        }

        fanScoreQueryRepository.addBookingScore(userId, artistId, POINTS_PER_CONFIRMED_BOOKING);
    }

    @Transactional
    public void syncArtistFanScoresFromConfirmedBookings() {
        List<FanScoreQueryRepository.ConfirmedArtistBookingCountRow> aggregates =
                fanScoreQueryRepository.findConfirmedArtistBookingCounts();

        Map<UserArtistKey, Integer> bookingScoreByKey = new HashMap<>();
        for (FanScoreQueryRepository.ConfirmedArtistBookingCountRow row : aggregates) {
            if (row.userId() == null || row.artistId() == null) {
                continue;
            }

            long count = row.confirmedBookingCount() == null ? 0L : row.confirmedBookingCount();
            int calculatedScore = Math.toIntExact(Math.max(0L, count) * POINTS_PER_CONFIRMED_BOOKING);
            bookingScoreByKey.put(new UserArtistKey(row.userId(), row.artistId()), calculatedScore);
        }

        List<UserArtistFanScore> existingScores = userArtistFanScoreRepository.findAll();
        for (UserArtistFanScore existingScore : existingScores) {
            UserArtistKey key = new UserArtistKey(existingScore.getUserId(), existingScore.getArtistId());
            Integer bookingScore = bookingScoreByKey.remove(key);
            existingScore.replaceBookingScore(bookingScore == null ? 0 : bookingScore);
        }

        for (Map.Entry<UserArtistKey, Integer> entry : bookingScoreByKey.entrySet()) {
            UserArtistKey key = entry.getKey();
            UserArtistFanScore score = UserArtistFanScore.builder()
                    .userId(key.userId())
                    .artistId(key.artistId())
                    .build();
            score.replaceBookingScore(entry.getValue());
            userArtistFanScoreRepository.save(score);
        }

        log.info("Artist fan score sync complete: {} aggregate rows processed", aggregates.size());
    }

    private record UserArtistKey(Long userId, Long artistId) {
    }
}
