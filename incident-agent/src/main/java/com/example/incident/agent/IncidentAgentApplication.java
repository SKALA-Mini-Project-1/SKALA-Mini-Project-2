package com.example.incident.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IncidentAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(IncidentAgentApplication.class, args);
    }
}
