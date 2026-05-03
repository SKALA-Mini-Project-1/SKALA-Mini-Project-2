package com.example.SKALA_Mini_Project_1.modules.waiting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.SKALA_Mini_Project_1.integration.concert.ConcertServiceClient;
import com.example.SKALA_Mini_Project_1.integration.userauth.UserAuthClient;
import com.example.SKALA_Mini_Project_1.modules.waiting.exception.DownstreamServiceException;
import com.example.SKALA_Mini_Project_1.modules.waiting.observability.QueueMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueuePriorityPolicyTest {

    private ConcertServiceClient concertServiceClient;
    private UserAuthClient userAuthClient;
    private QueuePriorityPolicy queuePriorityPolicy;

    @BeforeEach
    void setUp() {
        concertServiceClient = mock(ConcertServiceClient.class);
        userAuthClient = mock(UserAuthClient.class);
        QueueMetrics queueMetrics = new QueueMetrics(new SimpleMeterRegistry(), ObservationRegistry.create());
        queuePriorityPolicy = new QueuePriorityPolicy(concertServiceClient, userAuthClient, queueMetrics);
    }

    @Test
    void resolvesBoostMillisFromArtistFanScore() {
        when(concertServiceClient.getArtistIdForConcert(2L)).thenReturn(7L);
        when(userAuthClient.getArtistFanScore(1L, 7L)).thenReturn(3000);

        long boostMillis = queuePriorityPolicy.resolveBoostMillis(1L, 2L);

        assertThat(boostMillis).isEqualTo(3000L);
    }

    @Test
    void returnsNeutralBoostWhenDownstreamLookupFails() {
        when(concertServiceClient.getArtistIdForConcert(2L))
                .thenThrow(new DownstreamServiceException("concert down"));

        long boostMillis = queuePriorityPolicy.resolveBoostMillis(1L, 2L);

        assertThat(boostMillis).isZero();
    }

    @Test
    void returnsNeutralBoostWhenArtistLookupIsMissing() {
        when(concertServiceClient.getArtistIdForConcert(2L))
                .thenThrow(new IllegalArgumentException("artist missing"));

        long boostMillis = queuePriorityPolicy.resolveBoostMillis(1L, 2L);

        assertThat(boostMillis).isZero();
    }
}
