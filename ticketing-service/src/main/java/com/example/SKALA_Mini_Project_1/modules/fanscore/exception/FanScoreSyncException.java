package com.example.SKALA_Mini_Project_1.modules.fanscore.exception;

public class FanScoreSyncException extends RuntimeException {

    public FanScoreSyncException(String message) {
        super(message);
    }

    public FanScoreSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
