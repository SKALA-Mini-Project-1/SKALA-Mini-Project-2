package com.example.SKALA_Mini_Project_1.modules.seats.controller;

import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import com.example.SKALA_Mini_Project_1.modules.seats.dto.SeatMapResponse;
import com.example.SKALA_Mini_Project_1.modules.seats.service.SeatMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/concerts")
@Tag(name = "콘서트", description = "콘서트 좌석 맵 조회 API")
public class ConcertSeatController {

    private final SeatMapService seatMapService;
    private final RedisTemplate<String, String> redisTemplate;

    @GetMapping("/{concertId}/seats")
    @Operation(
            summary = "콘서트 좌석 맵 조회",
            description = "요청 헤더의 JWT 사용자 기준으로 좌석 상태와 선점 정보를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "대기열 입장 토큰 필요 또는 입장권한 없음")
    })
    public ResponseEntity<SeatMapResponse> getSeatMap(
            @PathVariable Long concertId,
            @RequestParam Long scheduleId
    ) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        ResponseEntity<SeatMapResponse> denied = ensureSeatEntryAccess(userId, concertId, scheduleId);
        if (denied != null) {
            return denied;
        }

        Long seatAccessTtlSeconds = resolveSeatAccessTtlSeconds(
                RedisKeyGenerator.seatAccessKey(userId, concertId, scheduleId)
        );
        return ResponseEntity.ok(seatMapService.getSeatMap(concertId, scheduleId, userId, seatAccessTtlSeconds));
    }

    @GetMapping("/schedules/{scheduleId}/seats")
    @Operation(
            summary = "스케줄 기준 좌석 맵 조회",
            description = "요청 헤더의 JWT 사용자 기준으로 특정 스케줄의 좌석 상태와 선점 정보를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<SeatMapResponse> getSeatMapBySchedule(@PathVariable("scheduleId") Long scheduleId) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        String accessBySchedule = redisTemplate.opsForValue()
                .get(RedisKeyGenerator.seatAccessByScheduleKey(userId, scheduleId));
        if (accessBySchedule == null) {
            return ResponseEntity.status(403).build();
        }

        Long seatAccessTtlSeconds = resolveSeatAccessTtlSeconds(
                RedisKeyGenerator.seatAccessByScheduleKey(userId, scheduleId)
        );
        return ResponseEntity.ok(seatMapService.getSeatMapBySchedule(scheduleId, userId, seatAccessTtlSeconds));
    }

    private ResponseEntity<SeatMapResponse> ensureSeatEntryAccess(
            Long userId,
            Long concertId,
            Long scheduleId
    ) {
        String accessKey = RedisKeyGenerator.seatAccessKey(userId, concertId, scheduleId);
        if (redisTemplate.hasKey(accessKey) == Boolean.TRUE) {
            return null;
        }
        return ResponseEntity.status(403).build();
    }

    private Long resolveSeatAccessTtlSeconds(String accessKey) {
        Long ttlSeconds = redisTemplate.getExpire(accessKey, TimeUnit.SECONDS);
        if (ttlSeconds == null || ttlSeconds < 0) {
            return 0L;
        }
        return ttlSeconds;
    }
}
