package com.example.SKALA_Mini_Project_1.modules.payments.exception;

public class TossPaymentsException extends RuntimeException {
    public TossPaymentsException(String message) {
        super(message);
    }

    public TossPaymentsException(String message, Throwable cause) {
        super(message, cause);
    }
}
