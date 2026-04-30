package com.example.SKALA_Mini_Project_1.modules.concerts.controller;

import com.example.SKALA_Mini_Project_1.modules.concerts.service.ConcertQueryService;
import com.example.SKALA_Mini_Project_1.modules.concerts.service.InternalApiGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/concerts")
public class InternalConcertController {

    private final InternalApiGuard internalApiGuard;
    private final ConcertQueryService concertQueryService;

    @GetMapping("/{concertId}/schedules/{scheduleId}")
    public ResponseEntity<Void> getScheduleForConcert(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @PathVariable Long concertId,
            @PathVariable Long scheduleId
    ) {
        internalApiGuard.validate(apiKey);
        if (!concertQueryService.existsScheduleForConcert(concertId, scheduleId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{concertId}/artist-id")
    public ResponseEntity<Long> getArtistIdForConcert(
            @RequestHeader(InternalApiGuard.HEADER_NAME) String apiKey,
            @PathVariable Long concertId
    ) {
        internalApiGuard.validate(apiKey);
        Long artistId = concertQueryService.getArtistIdByConcertId(concertId);
        if (artistId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(artistId);
    }
}
