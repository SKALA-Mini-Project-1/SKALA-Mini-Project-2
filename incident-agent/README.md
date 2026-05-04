# incident-agent

이 디렉토리는 탐지된 장애를 LLM으로 분석하는 Spring Boot 서비스입니다. `incident-detector`가 만든 장애 데이터를 읽고, 프롬프트를 구성해 OpenAI에 분석을 요청한 뒤 결과 버전을 저장합니다.

## 이 서비스가 하는 일

- 분석 대상 장애 조회
- 장애 문맥 데이터 조합
- 시스템 프롬프트와 입력 프롬프트 생성
- OpenAI API 호출
- 분석 결과 검증과 버전 저장

## 처음 보면 좋은 파일

- `src/main/java/.../IncidentAgentApplication.java`: 서비스 시작점
- `src/main/java/.../analysis/AnalysisService.java`: 분석 흐름 오케스트레이션
- `src/main/java/.../analysis/AnalysisInputBuilder.java`: 분석 입력 조합
- `src/main/java/.../llm/OpenAiLlmClient.java`: LLM 호출
- `src/main/java/.../scheduler/AnalysisJobScheduler.java`: 분석 작업 스케줄러

## 디렉토리 구조

```text
incident-agent/
├── build.gradle
├── Dockerfile
├── src/
│   └── main/
│       ├── java/com/example/incident/agent/
│       │   ├── analysis/      분석 조립과 결과 검증
│       │   ├── domain/        장애와 분석 버전 엔티티
│       │   ├── llm/           OpenAI 연동 클라이언트
│       │   ├── prompt/        시스템 프롬프트 구성
│       │   └── scheduler/     분석 작업 실행
│       └── resources/
└── README.md
```

## 구조를 읽는 방법

- 분석 파이프라인은 `analysis/`
- 실제 LLM 호출은 `llm/`
- 어떤 지시문으로 분석하는지는 `prompt/`
