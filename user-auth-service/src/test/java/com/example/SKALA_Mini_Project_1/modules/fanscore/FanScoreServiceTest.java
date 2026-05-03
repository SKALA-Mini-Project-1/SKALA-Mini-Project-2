package com.example.SKALA_Mini_Project_1.modules.fanscore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FanScoreServiceTest {

    private UserArtistFanScoreRepository userArtistFanScoreRepository;
    private FanScoreService fanScoreService;

    @BeforeEach
    void setUp() {
        userArtistFanScoreRepository = mock(UserArtistFanScoreRepository.class);
        FanScoreMetrics fanScoreMetrics = new FanScoreMetrics(new SimpleMeterRegistry(), ObservationRegistry.create());
        fanScoreService = new FanScoreService(userArtistFanScoreRepository, fanScoreMetrics);
    }

    @Test
    void returnsZeroWhenArtistFanScoreDoesNotExist() {
        when(userArtistFanScoreRepository.findByUserIdAndArtistId(1L, 2L)).thenReturn(Optional.empty());

        int score = fanScoreService.getArtistFanScore(1L, 2L);

        assertThat(score).isZero();
    }

    @Test
    void appliesConfirmedBookingScoreToExistingArtistScore() {
        UserArtistFanScore score = UserArtistFanScore.builder()
                .userId(1L)
                .artistId(2L)
                .bookingScore(1000)
                .externalScore(200)
                .totalScore(1200)
                .build();
        when(userArtistFanScoreRepository.findByUserIdAndArtistId(1L, 2L)).thenReturn(Optional.of(score));
        when(userArtistFanScoreRepository.save(score)).thenReturn(score);

        int totalScore = fanScoreService.applyAttendanceConfirmed(1L, 2L, UUID.randomUUID());

        assertThat(totalScore).isEqualTo(2200);
        verify(userArtistFanScoreRepository).save(score);
    }

    @Test
    void rejectsInvalidAttendanceConfirmedRequest() {
        assertThrows(IllegalArgumentException.class, () ->
                fanScoreService.applyAttendanceConfirmed(1L, 2L, null)
        );
    }
}
