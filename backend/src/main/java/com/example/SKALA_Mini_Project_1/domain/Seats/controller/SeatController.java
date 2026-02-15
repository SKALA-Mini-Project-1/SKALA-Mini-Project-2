package com.example.SKALA_Mini_Project_1.domain.Seats.controller;

import com.example.SKALA_Mini_Project_1.domain.Seats.dto.RequestSeatsDto;
import com.example.SKALA_Mini_Project_1.domain.Seats.service.SeatReservationService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seats")
public class SeatController {

    private final SeatReservationService seatReservationService;
    private final RedisTemplate<String, String> redisTemplate; 
    

    @PostMapping("/{seatId}/hold")
    public ResponseEntity<?> reserveSeat(@RequestBody @Valid RequestSeatsDto requestDto) {
        try {
            seatReservationService.reserveSeatTemporary(
                    requestDto.getConcertId(),
                    requestDto.getSeatId(),
                    requestDto.getSection(),
                    requestDto.getRowNumber(),
                    requestDto.getSeatNumber(),
                    requestDto.getUserId()
            );

            return ResponseEntity.ok(Map.of(
                    "message", "좌석이 성공적으로 선점되었습니다.",
                    "status", "success"
            ));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "message", e.getMessage(),
                    "status", "not_found"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", e.getMessage(),
                    "status", "bad_request"
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", e.getMessage(),
                    "status", "conflict"
            ));
        }
    }

    @GetMapping("/seats")
    public ResponseEntity<?> enterSeat(
            @RequestParam String token
    ) {

        String key = "seat:entry:" + token;

        String userId = redisTemplate.opsForValue().get(key);

        if (userId == null) {
            return ResponseEntity.status(403)
                    .body("유효하지 않은 접근");
        }

        // 1회용 처리
        redisTemplate.delete(key);

        return ResponseEntity.ok("좌석 선택 화면 입장 성공");
    }
}
