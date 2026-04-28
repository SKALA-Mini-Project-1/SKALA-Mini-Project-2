package com.example.SKALA_Mini_Project_1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.example.SKALA_Mini_Project_1.common",
        "com.example.SKALA_Mini_Project_1.global",
        "com.example.SKALA_Mini_Project_1.modules.concerts",
        "com.example.SKALA_Mini_Project_1.modules.seats"
})
public class ConcertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConcertServiceApplication.class, args);
    }
}
