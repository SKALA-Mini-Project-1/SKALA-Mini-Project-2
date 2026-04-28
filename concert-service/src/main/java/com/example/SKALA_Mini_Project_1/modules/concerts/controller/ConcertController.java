package com.example.SKALA_Mini_Project_1.modules.concerts.controller;

import com.example.SKALA_Mini_Project_1.modules.concerts.dto.ConcertResponse;
import com.example.SKALA_Mini_Project_1.modules.concerts.service.ConcertQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertQueryService concertQueryService;

    @GetMapping
    public ResponseEntity<List<ConcertResponse>> getConcerts() {
        return ResponseEntity.ok(concertQueryService.getVisibleConcerts());
    }

    @GetMapping("/{concertId}")
    public ResponseEntity<?> getConcert(@PathVariable Long concertId) {
        ConcertResponse concert = concertQueryService.getVisibleConcertById(concertId);
        if (concert == null) {
            return ResponseEntity.status(404).body(Map.of("message", "콘서트를 찾을 수 없습니다."));
        }
        return ResponseEntity.ok(concert);
    }
}
