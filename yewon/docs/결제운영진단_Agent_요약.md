# 결제 운영 진단 Agent 요약

## 1. 이 문서의 목적

이 문서는 팀원이 `결제 운영 진단 Agent`의 구조와 역할을 빠르게 이해하기 위한 요약 문서다.

핵심 질문은 아래 세 가지다.

- 이 Agent는 무엇을 하는가
- 기존 서비스 사이에서 어디에 위치하는가
- 운영자에게 어떤 가치를 주는가

## 2. Agent 한 줄 설명

`결제 운영 진단 Agent는 Payment/Ticketing 상태 불일치를 incident로 묶고, AI로 원인과 다음 액션을 설명해 운영자가 더 빨리 판단하도록 돕는 운영 보조 시스템이다.`

## 3. 왜 필요한가

결제 문제는 단순 성공/실패만으로 설명되지 않는다.

- PG 승인은 났지만 내부 상태가 미확정일 수 있다.
- 결제는 성공했지만 booking/seat 후처리가 누락될 수 있다.
- 사용자는 떠났는데 좌석 hold가 남을 수 있다.
- 같은 booking에서 중복 결제가 발생할 수 있다.

이런 문제는 한 서비스 로그만 봐서는 파악이 어렵고, 여러 상태를 함께 비교해야 한다.

## 4. 전체 구조

### 4.1 서비스 구성

- `Ticketing Svc`
- `Payment Svc`
- `Kafka`
- `incident-detector`
- `incident-agent`
- `incident-api`
- `Incident Store`

### 4.2 흐름

1. Ticketing/Payment 서비스가 상태 변화를 이벤트로 발행한다.
2. detector가 Kafka 이벤트를 읽고 이상 incident를 생성한다.
3. agent가 incident를 해석하고 summary, 원인, 권장 액션을 만든다.
4. api가 운영자 화면에 incident 목록/상세/재해석 기능을 제공한다.

## 5. 핵심 역할 분담

### detector

- 룰 기반 이상 탐지
- incident 생성/갱신
- severity 기본 결정

### agent

- incident 해석
- 자연어 summary 생성
- 원인 추정
- 운영 절차 수준의 액션 제안

### api

- 운영자 목록 조회
- incident 상세 조회
- 재해석 요청
- ACK/RESOLVE 처리

## 6. 다루는 incident 유형

v1 범위:

- `중복 결제`
- `유령 주문`
- `좀비 예약`
- `미확정 결제`

## 7. detector와 agent의 차이

### detector가 하는 일

- “이상 여부”를 기계적으로 판단
- 예: `payment.paid 후 5분 내 booking.confirmed 없음`

### agent가 하는 일

- “이게 어떤 사건인지”
- “왜 이렇게 보이는지”
- “운영자가 뭘 먼저 봐야 하는지”

즉, detector는 탐지 엔진이고, agent는 해석 계층이다.

## 8. 운영자에게 보이는 결과

목록 화면:

- incident type
- severity
- status
- latest summary

상세 화면:

- summary
- suspected root cause
- recommended actions
- current state snapshot
- timeline
- analysis 이력

상세 화면은 `요약 우선 + 근거 드릴다운` 구조를 따른다.

## 9. 상태 흐름

incident lifecycle:

- `OPEN`
- `ANALYZING`
- `ANALYZED`
- `ACKNOWLEDGED`
- `RESOLVED`

운영자는 초기 버전에서 다음만 수행한다.

- 조회
- 재해석 요청
- ACKNOWLEDGED 처리
- RESOLVED 처리

복구 버튼과 자동 실행은 후속 확장이다.

## 10. 운영 원칙

- Kafka 이벤트는 outbox 패턴 기반으로 신뢰성 있게 발행한다.
- detector/agent/api는 Kubernetes에서 각각 별도 Deployment로 배치한다.
- incident와 analysis는 PostgreSQL + JSONB에 저장한다.
- LLM 실패 시에도 incident는 운영자에게 즉시 보인다.
- PII는 Agent 입력과 로그에서 기본 제외/마스킹한다.

## 11. 구현 품질 원칙

상세 설계 문서에는 아래를 필수 규칙으로 넣는다.

- 하드코딩 금지
- secrets 분리
- config화 필수
- idempotency 보장
- schema versioning
- structured logging
- testability 확보
- observability 메트릭/알람 포함

## 12. 기대 효과

- 결제 이상 사건을 더 빨리 발견할 수 있다.
- 운영자가 여러 로그와 테이블을 직접 맞춰보는 시간을 줄일 수 있다.
- 장애 조사 방식이 표준화된다.
- 추후 관리자 툴과 복구 자동화로 확장하기 쉬워진다.

## 13. v1 이후 확장

- 복구 버튼
- 수동 보정 워크플로우
- 자동 복구
- 더 정교한 incident 군집화
- 비용 기반 agent 전략

