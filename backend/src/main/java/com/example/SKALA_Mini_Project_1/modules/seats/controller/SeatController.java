package com.example.SKALA_Mini_Project_1.modules.seats.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.SKALA_Mini_Project_1.modules.seats.dto.BatchSeatHoldRequest;
import com.example.SKALA_Mini_Project_1.modules.seats.dto.SeatSelectRequest;
import com.example.SKALA_Mini_Project_1.modules.seats.service.SeatReservationService;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seats")
@Tag(name = "좌석", description = "좌석 선점/해제 API")
public class SeatController {

    private final SeatReservationService seatReservationService;

    @PostMapping("/{seatId}/hold")
    @Operation(
            summary = "좌석 선점 또는 해제",
            description = "JWT 인증 사용자 기준으로 좌석을 선점합니다. 이미 같은 사용자가 선점한 좌석을 다시 요청하면 선점이 해제됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "선점 성공 또는 선점 해제 성공"),
            @ApiResponse(responseCode = "400", description = "요청 좌석 정보 불일치 또는 4매 초과"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "좌석 ID 없음"),
            @ApiResponse(responseCode = "409", description = "다른 사용자가 이미 선점했거나 판매 완료된 좌석")
    })
    public ResponseEntity<?> reserveSeat(@RequestBody @Valid SeatSelectRequest requestDto) {
        try {
            Long userId = (Long) SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal();

            SeatReservationService.SeatHoldResult result = seatReservationService.reserveSeatTemporary(
                    requestDto.getConcertId(),
                    requestDto.getSeatId(),
                    userId
            );

            if (result == SeatReservationService.SeatHoldResult.RELEASED) {
                return ResponseEntity.ok(Map.of(
                        "message", "선점한 좌석이 해제되었습니다.",
                        "status", "success",
                        "action", "released"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "좌석이 성공적으로 선점되었습니다.",
                    "status", "success",
                    "action", "held"
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

    @PostMapping("/holds")
    @Operation(
            summary = "좌석 일괄 선점",
            description = "JWT 인증 사용자 기준으로 최대 4개의 좌석을 원자적으로 선점합니다. 일부 실패 시 전체 선점이 취소되고 실패 좌석 목록을 반환합니다."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    examples = @ExampleObject(
                            name = "좌석 일괄 선점 요청 예시",
                            value = """
                                    {
                                      "concertId": 1,
                                      "seatIds": [101, 102, 103, 104]
                                    }
                                    """
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "일괄 선점 성공"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류 또는 4매 초과"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "409", description = "일부 좌석 선점 실패")
    })
    public ResponseEntity<?> holdSeatsBatch(@RequestBody @Valid BatchSeatHoldRequest request) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        try {
            SeatReservationService.BatchHoldResult result = seatReservationService.holdSeatsBatch(
                    request.getConcertId(),
                    request.getSeatIds(),
                    userId
            );

            if (!result.success()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "message", "일부 좌석 선점에 실패했습니다.",
                        "status", "conflict",
                        "failedSeatIds", result.failedSeatIds()
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "좌석 일괄 선점에 성공했습니다.",
                    "status", "success",
                    "heldSeatIds", result.heldSeatIds()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", e.getMessage(),
                    "status", "bad_request"
            ));
        }
    }

    @DeleteMapping("/hold")
    @Operation(
            summary = "좌석 선점 해제",
            description = "JWT 인증 사용자 기준으로 본인이 선점한 좌석을 해제합니다. 이미 해제된 좌석은 성공으로 처리됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "해제 성공 또는 이미 해제됨"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "좌석 ID 없음"),
            @ApiResponse(responseCode = "409", description = "다른 사용자가 선점한 좌석")
    })
    public ResponseEntity<?> releaseSeat(@RequestBody @Valid SeatSelectRequest requestDto) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        try {
            SeatReservationService.SeatReleaseResult result = seatReservationService.releaseSeatHold(
                    requestDto.getConcertId(),
                    requestDto.getSeatId(),
                    userId
            );

            if (result == SeatReservationService.SeatReleaseResult.ALREADY_RELEASED) {
                return ResponseEntity.ok(Map.of(
                        "message", "이미 해제된 좌석입니다.",
                        "status", "success",
                        "action", "already_released"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "좌석 선점이 해제되었습니다.",
                    "status", "success",
                    "action", "released"
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
}
