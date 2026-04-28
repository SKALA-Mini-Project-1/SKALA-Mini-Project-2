package com.example.SKALA_Mini_Project_1.global.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "email")
@Getter
@Setter
public class EmailProperties {
    
    private String fromAddress;
    private String fromName;
    
    private Verification verification = new Verification();
    
    @Getter
    @Setter
    public static class Verification {
        private int codeLength = 6;
        private int expirationMinutes = 5;
        private int resendIntervalSeconds = 60;
    }
}