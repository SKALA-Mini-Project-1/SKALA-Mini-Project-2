package com.example.SKALA_Mini_Project_1.integration.userauth;

public record InternalArtistFanScoreResponse(
        Long userId,
        Long artistId,
        int totalScore
) {
}
