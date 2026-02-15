package com.example.SKALA_Mini_Project_1.modules.bookings.controller;

import com.example.SKALA_Mini_Project_1.modules.bookings.dto.CreateBookingRequest;
import com.example.SKALA_Mini_Project_1.modules.bookings.dto.CreateBookingResponse;
import com.example.SKALA_Mini_Project_1.modules.bookings.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "예약", description = "예약 생성 API")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(
            summary = "예약 생성",
            description = "JWT 인증 사용자 기준으로 선점된 좌석을 예약(Booking)으로 저장합니다."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    examples = @ExampleObject(
                            name = "예약 생성 요청 예시",
                            value = """
                                    {
                                      "concertId": 1,
                                      "seatIds": [401, 402]
                                    }
                                    """
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "예약 생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "409", description = "좌석 상태/선점 충돌")
    })
    public ResponseEntity<?> createBooking(@RequestBody @Valid CreateBookingRequest request) {
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        try {
            CreateBookingResponse response = bookingService.createBooking(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "bad_request",
                    "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "conflict",
                    "message", e.getMessage()
            ));
        }
    }
}
