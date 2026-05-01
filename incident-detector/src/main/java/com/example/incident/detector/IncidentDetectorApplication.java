package com.example.incident.detector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IncidentDetectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentDetectorApplication.class, args);
    }
}
