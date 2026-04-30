package com.example.SKALA_Mini_Project_1.common;

import java.time.LocalDateTime;

import lombok.Getter;

@Getter
public class ErrorResponse {
    private int status;
    private String message;
    private LocalDateTime timestamp;
    private Object details;

    private ErrorResponse(int status, String message, LocalDateTime timestamp, Object details) {
        this.status = status;
        this.message = message;
        this.timestamp = timestamp;
        this.details = details;
    }
    
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, LocalDateTime.now(), null);
    }

    public static ErrorResponse of(int status, String message, Object details) {
        return new ErrorResponse(status, message, LocalDateTime.now(), details);
    }
}
