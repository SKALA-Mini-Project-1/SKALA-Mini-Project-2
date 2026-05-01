package com.example.SKALA_Mini_Project_1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableKafka
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.example.SKALA_Mini_Project_1.common",
        "com.example.SKALA_Mini_Project_1.global",
        "com.example.SKALA_Mini_Project_1.kafka",
        "com.example.SKALA_Mini_Project_1.integration",
        "com.example.SKALA_Mini_Project_1.modules.bookings",
        "com.example.SKALA_Mini_Project_1.modules.seats",
        "com.example.SKALA_Mini_Project_1.modules.fanscore",
        "com.example.SKALA_Mini_Project_1.modules.events",
        "com.example.SKALA_Mini_Project_1.modules.finalization",
        "com.example.SKALA_Mini_Project_1.modules.reconciliation"
})
public class TicketingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketingServiceApplication.class, args);
    }
}
