package com.example.SKALA_Mini_Project_1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "com.example.SKALA_Mini_Project_1.common",
        "com.example.SKALA_Mini_Project_1.global",
        "com.example.SKALA_Mini_Project_1.integration",
        "com.example.SKALA_Mini_Project_1.modules.waiting",
        "com.example.SKALA_Mini_Project_1.modules.fanscore"
})
public class QueueServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueueServiceApplication.class, args);
    }
}
