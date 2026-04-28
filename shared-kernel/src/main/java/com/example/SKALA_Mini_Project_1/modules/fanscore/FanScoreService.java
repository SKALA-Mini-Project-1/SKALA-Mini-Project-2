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
    public static final int MAX_QUEUE_PRIORITY_BOOST_MILLIS = 5000;

    private final UserArtistFanScoreRepository userArtistFanScoreRepository;
    private final FanScoreQueryRepository fanScoreQueryRepository;

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

    public int getArtistFanScoreForConcert(Long userId, Long concertId) {
        if (userId == null || concertId == null) {
            return 0;
        }

        return fanScoreQueryRepository.findArtistIdByConcertId(concertId)
                .map(artistId -> getArtistFanScore(userId, artistId))
                .orElse(0);
    }

    public int getQueuePriorityBoostMillis(Long userId, Long concertId) {
        int artistFanScore = getArtistFanScoreForConcert(userId, concertId);
        return Math.min(Math.max(artistFanScore, 0), MAX_QUEUE_PRIORITY_BOOST_MILLIS);
    }

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
