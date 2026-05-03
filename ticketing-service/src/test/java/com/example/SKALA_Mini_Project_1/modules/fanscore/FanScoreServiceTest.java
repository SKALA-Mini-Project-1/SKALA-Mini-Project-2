package com.example.SKALA_Mini_Project_1.modules.fanscore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.SKALA_Mini_Project_1.integration.concert.ConcertServiceClient;
import com.example.SKALA_Mini_Project_1.integration.userauth.UserAuthClient;
import com.example.SKALA_Mini_Project_1.modules.bookings.repository.BookingRepository;
import com.example.SKALA_Mini_Project_1.modules.fanscore.exception.FanScoreSyncException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FanScoreServiceTest {

    private BookingRepository bookingRepository;
    private FanScoreQueryRepository fanScoreQueryRepository;
    private ConcertServiceClient concertServiceClient;
    private UserAuthClient userAuthClient;
    private FanScoreService fanScoreService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        fanScoreQueryRepository = mock(FanScoreQueryRepository.class);
        concertServiceClient = mock(ConcertServiceClient.class);
        userAuthClient = mock(UserAuthClient.class);
        FanScoreMetrics fanScoreMetrics = new FanScoreMetrics(new SimpleMeterRegistry(), ObservationRegistry.create());
        fanScoreService = new FanScoreService(
                bookingRepository,
                fanScoreQueryRepository,
                concertServiceClient,
                userAuthClient,
                fanScoreMetrics
        );
    }

    @Test
    void skipsImmediateApplicationWhenBookingIsNotYetEligible() {
        UUID bookingId = UUID.randomUUID();

        when(fanScoreQueryRepository.findEligibleConfirmedBookingTarget(eq(bookingId), any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        fanScoreService.applyConfirmedBookingScore(bookingId, 1L);

        verify(userAuthClient, never()).applyAttendanceConfirmedFanScore(any(), any(), any(), any(), any());
        verify(bookingRepository, never()).markFanScoreApplied(any(), any());
    }

    @Test
    void syncsEligibleBookingAndMarksItApplied() {
        UUID bookingId = UUID.randomUUID();
        FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow target =
                new FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow(bookingId, 10L, 20L);

        when(fanScoreQueryRepository.findEligibleConfirmedBookingTargets(any(OffsetDateTime.class)))
                .thenReturn(List.of(target));
        when(concertServiceClient.getArtistIdForConcert(20L)).thenReturn(30L);
        when(bookingRepository.markFanScoreApplied(eq(bookingId), any(OffsetDateTime.class))).thenReturn(1);

        int appliedCount = fanScoreService.syncArtistFanScoresFromConfirmedBookings();

        assertThat(appliedCount).isEqualTo(1);
        verify(userAuthClient).applyAttendanceConfirmedFanScore(eq(10L), eq(bookingId), eq(20L), eq(30L), any());
        verify(bookingRepository).markFanScoreApplied(eq(bookingId), any(OffsetDateTime.class));
    }

    @Test
    void continuesSyncWhenOneTargetFails() {
        UUID firstBookingId = UUID.randomUUID();
        UUID secondBookingId = UUID.randomUUID();
        FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow first =
                new FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow(firstBookingId, 10L, 20L);
        FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow second =
                new FanScoreQueryRepository.ConfirmedBookingFanScoreTargetRow(secondBookingId, 11L, 21L);

        when(fanScoreQueryRepository.findEligibleConfirmedBookingTargets(any(OffsetDateTime.class)))
                .thenReturn(List.of(first, second));
        when(concertServiceClient.getArtistIdForConcert(20L))
                .thenThrow(new FanScoreSyncException("concert unavailable"));
        when(concertServiceClient.getArtistIdForConcert(21L)).thenReturn(31L);
        when(bookingRepository.markFanScoreApplied(eq(secondBookingId), any(OffsetDateTime.class))).thenReturn(1);

        int appliedCount = fanScoreService.syncArtistFanScoresFromConfirmedBookings();

        assertThat(appliedCount).isEqualTo(1);
        verify(userAuthClient, never()).applyAttendanceConfirmedFanScore(eq(10L), eq(firstBookingId), eq(20L), any(), any());
        verify(userAuthClient).applyAttendanceConfirmedFanScore(eq(11L), eq(secondBookingId), eq(21L), eq(31L), any());
    }
}
