package com.example.SKALA_Mini_Project_1.modules.seats.controller;

import com.example.SKALA_Mini_Project_1.modules.seats.dto.SeatMapResponse;
import com.example.SKALA_Mini_Project_1.modules.seats.service.SeatMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/concerts")
@Tag(name = "콘서트", description = "콘서트 좌석 맵 조회 API")
public class ConcertSeatController {

    private final SeatMapService seatMapService;

    @GetMapping("/{concertId}/seats")
    @Operation(
            summary = "콘서트 좌석 맵 조회",
            description = "요청 헤더의 JWT 사용자 기준으로 좌석 상태와 선점 정보를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<SeatMapResponse> getSeatMap(@PathVariable Long concertId) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        return ResponseEntity.ok(seatMapService.getSeatMap(concertId, userId));
    }
}
