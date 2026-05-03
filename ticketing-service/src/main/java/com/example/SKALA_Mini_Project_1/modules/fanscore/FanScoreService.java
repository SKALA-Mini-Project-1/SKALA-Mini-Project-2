package com.example.SKALA_Mini_Project_1.modules.fanscore;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.example.SKALA_Mini_Project_1.integration.concert.ConcertServiceClient;
import com.example.SKALA_Mini_Project_1.integration.userauth.UserAuthClient;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingRepository;
import com.example.SKALA_Mini_Project_1.modules.fanscore.exception.FanScoreSyncException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FanScoreService {

    private final BookingRepository bookingRepository;
    private final FanScoreQueryRepository fanScoreQueryRepository;
    private final ConcertServiceClient concertServiceClient;
    private final UserAuthClient userAuthClient;
    private final FanScoreMetrics fanScoreMetrics;

    @Transactional
    public void applyConfirmedBookingScore(UUID bookingId, Long userId) {
        fanScoreMetrics.observe(
                "fairline.fan_score.sync.single",
                "fanScoreService#applyConfirmedBookingScore",
                "apply_confirmed_booking",
                () -> {
                    if (bookingId == null || userId == null) {
                        fanScoreMetrics.incrementSyncRequest("apply_confirmed_booking", "invalid_input");
                        return null;
                    }

                    OffsetDateTime referenceTime = nowUtc();
                    var maybeTarget = fanScoreQueryRepository.findEligibleConfirmedBookingTarget(bookingId, referenceTime);
                    if (maybeTarget.isEmpty()) {
                        fanScoreMetrics.incrementSyncRequest("apply_confirmed_booking", "not_eligible");
                        return null;
                    }

                    boolean applied = applyTarget(maybeTarget.get(), referenceTime);
                    fanScoreMetrics.incrementSyncRequest(
                            "apply_confirmed_booking",
                            applied ? "applied" : "skipped"
                    );
                    return null;
                }
        );
    }

    @Transactional
    public int syncArtistFanScoresFromConfirmedBookings() {
        return fanScoreMetrics.observe(
                "fairline.fan_score.sync.batch",
                "fanScoreService#syncArtistFanScoresFromConfirmedBookings",
                "sync_batch",
                () -> {
                    OffsetDateTime referenceTime = nowUtc();
                    List<FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow> targets =
                            fanScoreQueryRepository.findEligibleConfirmedBookingTargets(referenceTime);

                    fanScoreMetrics.recordBatchTargetCount(targets.size());

                    int appliedCount = 0;
                    for (FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow target : targets) {
                        if (applyTarget(target, referenceTime)) {
                            appliedCount++;
                        }
                    }

                    fanScoreMetrics.recordAppliedCount(appliedCount);
                    fanScoreMetrics.incrementSyncRequest("sync_batch", "completed");
                    log.info("Fan score sync complete: {} booking targets applied", appliedCount);
                    return appliedCount;
                }
        );
    }

    private boolean applyTarget(
            FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow target,
            OffsetDateTime appliedAt
    ) {
        try {
            Long artistId = concertServiceClient.getArtistIdForConcert(target.concertId());
            if (artistId == null) {
                fanScoreMetrics.incrementTarget("skipped_missing_artist");
                return false;
            }

            userAuthClient.applyAttendanceConfirmedFanScore(
                    target.userId(),
                    target.bookingId(),
                    target.concertId(),
                    artistId,
                    appliedAt
            );
            bookingRepository.markFanScoreApplied(target.bookingId(), appliedAt);
            fanScoreMetrics.incrementTarget("applied");
            return true;
        } catch (IllegalArgumentException | FanScoreSyncException e) {
            fanScoreMetrics.incrementTarget("skipped_downstream");
            log.warn(
                    "Fan score sync skipped for bookingId={} userId={} concertId={}: {}",
                    target.bookingId(),
                    target.userId(),
                    target.concertId(),
                    e.getMessage()
            );
            return false;
        }
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
