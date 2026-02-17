package com.example.SKALA_Mini_Project_1.modules.waiting.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueueStatusResponse {

    private boolean enter;
    private String entryToken;
    private Long rank;

    public static QueueStatusResponse enter(String token) {
        return new QueueStatusResponse(true, token, null);
    }

    public static QueueStatusResponse waiting(Long rank) {
        return new QueueStatusResponse(false, null, rank);
    }
}