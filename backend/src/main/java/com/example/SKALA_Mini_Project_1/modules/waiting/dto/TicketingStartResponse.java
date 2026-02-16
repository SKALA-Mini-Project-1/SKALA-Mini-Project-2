package com.example.SKALA_Mini_Project_1.modules.waiting.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TicketingStartResponse {

    private boolean enter;     // 좌석 서버 바로 입장 여부
    private String entryToken; // 입장 토큰 (있을 경우)
    private Long rank;         // 대기 순번 (대기일 경우)

    public static TicketingStartResponse enter(String token) {
        return TicketingStartResponse.builder()
                .enter(true)
                .entryToken(token)
                .build();
    }

    public static TicketingStartResponse waiting(Long rank) {
        return TicketingStartResponse.builder()
                .enter(false)
                .rank(rank)
                .build();
    }
}