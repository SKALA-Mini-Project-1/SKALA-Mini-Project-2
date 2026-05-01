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

    @Transactional
    public void applyConfirmedBookingScore(UUID bookingId, Long userId) {
        if (bookingId == null || userId == null) {
            return;
        }

        OffsetDateTime referenceTime = nowUtc();
        fanScoreQueryRepository.findEligibleConfirmedBookingTarget(bookingId, referenceTime)
                .ifPresent(target -> applyTarget(target, referenceTime));
    }

    @Transactional
    public int syncArtistFanScoresFromConfirmedBookings() {
        OffsetDateTime referenceTime = nowUtc();
        List<FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow> targets =
                fanScoreQueryRepository.findEligibleConfirmedBookingTargets(referenceTime);

        int appliedCount = 0;
        for (FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow target : targets) {
            if (applyTarget(target, referenceTime)) {
                appliedCount++;
            }
        }

        log.info("Fan score sync complete: {} booking targets applied", appliedCount);
        return appliedCount;
    }

    private boolean applyTarget(
            FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow target,
            OffsetDateTime appliedAt
    ) {
        try {
            Long artistId = concertServiceClient.getArtistIdForConcert(target.concertId());
            if (artistId == null) {
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
            return true;
        } catch (IllegalArgumentException | FanScoreSyncException e) {
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
