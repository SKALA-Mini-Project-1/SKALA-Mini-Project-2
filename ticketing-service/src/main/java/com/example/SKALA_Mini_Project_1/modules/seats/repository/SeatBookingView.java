package com.example.SKALA_Mini_Project_1.modules.seats.repository;

import java.math.BigDecimal;

public interface SeatBookingView {
    Long getSeatId();
    String getSection();
    Integer getRowNumber();
    Integer getSeatNumber();
    BigDecimal getPrice();
    String getStatus();
    Long getScheduleId();
}
