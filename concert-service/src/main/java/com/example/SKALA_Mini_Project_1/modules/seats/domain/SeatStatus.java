package com.example.SKALA_Mini_Project_1.modules.seats.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeatStatus {
    AVAILABLE("예약 가능"),
    HOLD("선점됨"),
    RESERVED("판매 완료");

    private final String description;
}
