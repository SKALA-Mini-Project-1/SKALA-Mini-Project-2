package com.example.SKALA_Mini_Project_1.modules.fanscore;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FanScoreSyncInitializer implements ApplicationRunner {

    private final FanScoreService fanScoreService;

    @Override
    public void run(ApplicationArguments args) {
        fanScoreService.syncArtistFanScoresFromConfirmedBookings();
        log.info("Fan score startup sync finished");
    }
}
