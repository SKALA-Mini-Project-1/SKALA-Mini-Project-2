package com.example.SKALA_Mini_Project_1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.example.SKALA_Mini_Project_1.common",
        "com.example.SKALA_Mini_Project_1.global",
        "com.example.SKALA_Mini_Project_1.modules.users",
        "com.example.SKALA_Mini_Project_1.modules.fanscore"
})
public class UserAuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserAuthServiceApplication.class, args);
    }
}
