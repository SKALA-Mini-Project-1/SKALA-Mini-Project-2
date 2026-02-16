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
                Authentication authentication
        ) {

        Long userId = (Long) authentication.getPrincipal();

        TicketingStartResponse response =queueService.startTicketing(concertId, userId);

        return ResponseEntity.ok(response);
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