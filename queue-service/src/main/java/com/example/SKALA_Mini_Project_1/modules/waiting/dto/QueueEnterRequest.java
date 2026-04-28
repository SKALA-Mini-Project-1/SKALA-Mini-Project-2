package com.example.SKALA_Mini_Project_1.modules.waiting.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueueEnterRequest {
    private Long concertId;
    private String userId;
    private Long fandomWeightMillis;
}
