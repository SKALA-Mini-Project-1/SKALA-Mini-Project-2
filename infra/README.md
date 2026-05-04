# infra

이 디렉토리는 로컬 개발 환경에서 사용하는 데이터베이스와 CDC 연결 자산을 모아 둔 곳입니다. 애플리케이션 코드가 아니라 `docker-compose.yaml`에서 참조하는 보조 인프라 설정이 들어 있습니다.

## 이 디렉토리가 하는 일

- Postgres 초기 스키마와 접근 정책 제공
- Debezium Kafka Connect 커넥터 등록 파일 제공
- 로컬에서 아웃박스/CDC 흐름을 붙일 수 있게 지원

## 디렉토리 구조

```text
infra/
├── debezium/
│   ├── payment-outbox-connector.json
│   ├── ticketing-outbox-connector.json
│   └── register-connectors.sh
├── postgres/
│   ├── init.sql
│   └── pg_hba.conf
└── README.md
```

## 구조를 읽는 방법

- DB 초기화가 궁금하면 `postgres/init.sql`
- CDC 연결 구성이 궁금하면 `debezium/*.json`
- 커넥터 등록 절차는 `debezium/register-connectors.sh`
