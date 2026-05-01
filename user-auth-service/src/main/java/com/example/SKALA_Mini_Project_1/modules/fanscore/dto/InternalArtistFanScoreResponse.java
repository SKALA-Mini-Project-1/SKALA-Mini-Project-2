package com.example.SKALA_Mini_Project_1.modules.fanscore.dto;

public record InternalArtistFanScoreResponse(
        Long userId,
        Long artistId,
        int totalScore
) {
}
