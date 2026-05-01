package com.example.SKALA_Mini_Project_1.modules.waiting.exception;

public class DownstreamServiceException extends RuntimeException {

    public DownstreamServiceException(String message) {
        super(message);
    }

    public DownstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
