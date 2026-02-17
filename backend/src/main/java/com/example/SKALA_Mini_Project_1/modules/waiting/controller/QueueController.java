package com.example.SKALA_Mini_Project_1.modules.waiting.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.example.SKALA_Mini_Project_1.modules.waiting.service.QueueService;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.QueueStatusResponse;
import com.example.SKALA_Mini_Project_1.modules.waiting.dto.TicketingStartResponse;


import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ticketing")
public class QueueController {
    private final QueueService queueService;

    @PostMapping("/start")
        public ResponseEntity<?> startTicketing(
                @RequestParam Long concertId,
                @RequestParam Long scheduleId,
                Authentication authentication
        ) {

        Long userId = (Long) authentication.getPrincipal();

        TicketingStartResponse response = queueService.startTicketing(concertId, scheduleId, userId);

        return ResponseEntity.ok(response);
        }

     @GetMapping("/status")
        public QueueStatusResponse getStatus(
                @RequestParam Long concertId,
                @RequestParam Long scheduleId,
                Authentication authentication
        ) {
        Long userId = (Long) authentication.getPrincipal();
        return queueService.getStatus(concertId, scheduleId, userId);
        }
}
