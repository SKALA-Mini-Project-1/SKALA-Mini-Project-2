package com.example.SKALA_Mini_Project_1.modules.waiting.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import java.util.Map;

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
                @RequestParam String concertCode,
                @RequestParam Long scheduleId,
                Authentication authentication
        ) {

        Long userId = (Long) authentication.getPrincipal();

        TicketingStartResponse response =queueService.startTicketing(concertCode, scheduleId, userId);

        return ResponseEntity.ok(response);
        }

     @GetMapping("/status")
        public QueueStatusResponse getStatus(
                @RequestParam String concertCode,
                @RequestParam Long scheduleId,
                Authentication authentication
        ) {
        Long userId = (Long) authentication.getPrincipal();
        return queueService.getStatus(concertCode, scheduleId, userId);
        }

    @PostMapping("/dev/seed-ahead")
    public ResponseEntity<?> seedAhead(
            @RequestParam String concertCode,
            @RequestParam Long scheduleId,
            @RequestParam(defaultValue = "150") int count,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        Long rank = queueService.seedQueueAheadForTest(concertCode, scheduleId, userId, count);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "seedCount", count,
                "rank", rank
        ));
    }

//     @GetMapping("/rank")
//     public ResponseEntity<?> rank(
//             @RequestParam Long concertId,
//             @RequestParam String userId) {

//         long rank = queueService.getRank(concertId, userId);

//         return ResponseEntity.ok(Map.of(
//                 "rank", rank
//         ));
//     }


//     @PostMapping("/try-enter-seat")
//         public ResponseEntity<?> tryEnterSeat(
//                 @RequestParam Long concertId,
//                 @RequestParam String userId
//         ) {

//         String token = queueService.tryEnterSeat(concertId, userId);

//         if (token == null) {
//                 return ResponseEntity.status(403)
//                         .body("아직 입장 불가");
//         }

//         return ResponseEntity.ok(Map.of(
//                 "entryToken", token,
//                 "redirectUrl", "http://localhost:8081/api/seats/seats?token=" + token
//         ));
//         }
}
