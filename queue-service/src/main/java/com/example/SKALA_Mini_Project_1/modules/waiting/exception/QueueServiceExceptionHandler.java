package com.example.SKALA_Mini_Project_1.modules.waiting.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.SKALA_Mini_Project_1.modules.waiting")
public class QueueServiceExceptionHandler {

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<Map<String, Object>> handleDownstreamServiceException(DownstreamServiceException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", 503,
                        "message", e.getMessage()
                ));
    }
}
