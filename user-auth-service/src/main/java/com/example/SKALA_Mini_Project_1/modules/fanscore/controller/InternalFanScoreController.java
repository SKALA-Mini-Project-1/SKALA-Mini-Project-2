package com.example.SKALA_Mini_Project_1.modules.fanscore.controller;

import com.example.SKALA_Mini_Project_1.modules.fanscore.FanScoreService;
import com.example.SKALA_Mini_Project_1.modules.fanscore.dto.InternalArtistFanScoreResponse;
import com.example.SKALA_Mini_Project_1.modules.fanscore.dto.InternalFanScoreApplyRequest;
import com.example.SKALA_Mini_Project_1.modules.users.InternalApiGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/fan-scores")
public class InternalFanScoreController {

    private final InternalApiGuard internalApiGuard;
    private final FanScoreService fanScoreService;

    @GetMapping("/users/{userId}/artists/{artistId}")
    public ResponseEntity<InternalArtistFanScoreResponse> getArtistFanScore(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @PathVariable Long userId,
            @PathVariable Long artistId
    ) {
        internalApiGuard.validate(apiKey);
        return ResponseEntity.ok(new InternalArtistFanScoreResponse(
                userId,
                artistId,
                fanScoreService.getArtistFanScore(userId, artistId)
        ));
    }

    @PostMapping("/events/attendance-confirmed")
    public ResponseEntity<InternalArtistFanScoreResponse> applyAttendanceConfirmed(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @Valid @RequestBody InternalFanScoreApplyRequest request
    ) {
        internalApiGuard.validate(apiKey);
        int totalScore = fanScoreService.applyAttendanceConfirmed(
                request.userId(),
                request.artistId(),
                request.bookingId()
        );
        return ResponseEntity.ok(new InternalArtistFanScoreResponse(
                request.userId(),
                request.artistId(),
                totalScore
        ));
    }
}
