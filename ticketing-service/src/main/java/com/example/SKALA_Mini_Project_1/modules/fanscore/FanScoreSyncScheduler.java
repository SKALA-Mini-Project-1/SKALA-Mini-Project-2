package com.example.SKALA_Mini_Project_1.modules.fanscore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FanScoreSyncScheduler {

    private final FanScoreService fanScoreService;

    @Scheduled(fixedDelayString = "${ticketing.fan-score.sync.fixed-delay-ms:60000}")
    public void syncConfirmedBookings() {
        int appliedCount = fanScoreService.syncArtistFanScoresFromConfirmedBookings();
        if (appliedCount > 0) {
            log.info("Fan score scheduler applied {} booking targets", appliedCount);
        }
    }
}
