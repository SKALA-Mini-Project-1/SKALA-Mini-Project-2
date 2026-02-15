package com.example.SKALA_Mini_Project_1.modules.waiting.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueueStatusResponse {
    private long rank;
    private boolean allowed;
    private String redirectUrl;
}
