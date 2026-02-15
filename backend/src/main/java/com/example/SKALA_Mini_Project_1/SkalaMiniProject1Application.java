package com.example.SKALA_Mini_Project_1;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.cdimascio.dotenv.Dotenv;

@EnableScheduling
@SpringBootApplication
public class SkalaMiniProject1Application {

	public static void main(String[] args) {
		// 실행 위치가 달라도 .env를 찾을 수 있도록 backend -> 현재 디렉토리 순서로 탐색
		loadEnvFromKnownDirectories(List.of("./backend", "./"));


		// 2. Spring 실행 전 시스템 변수에 값이 있는지 강제로 확인
		System.out.println("=== 환경 변수 점검 ===");
		System.out.println("DB_URL: " + System.getProperty("DB_URL"));
		System.out.println("DB_USER: " + System.getProperty("DB_USER"));
		System.out.println("====================");

		SpringApplication.run(SkalaMiniProject1Application.class, args);
	}

	private static void loadEnvFromKnownDirectories(List<String> directories) {
		for (String directory : directories) {
			Path envPath = Path.of(directory, ".env").normalize();
			if (!Files.exists(envPath)) {
				continue;
			}

			Dotenv dotenv = Dotenv.configure()
					.directory(directory)
					.ignoreIfMissing()
					.load();

			dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
			System.out.println(".env loaded from: " + envPath);
			return;
		}

		System.out.println(".env not found in known directories. Using existing system environment.");
	}
}
