# frontend

이 디렉토리는 사용자 예매 화면과 운영용 장애 화면을 제공하는 Vue 3 프론트엔드입니다. 콘서트 목록 조회부터 대기열, 좌석 선택, 결제 성공/실패 처리, 운영자용 장애 목록과 상세 화면까지 포함합니다.

## 이 프론트가 하는 일

- 로그인, 회원가입, 마이페이지
- 공연 목록과 상세 조회
- 대기열 입장과 진행 상태 표시
- 좌석 선택과 예약 생성
- 결제 진행과 리다이렉트 결과 처리
- 운영자용 장애 목록/상세 화면 제공

## 처음 보면 좋은 파일

- `src/App.vue`: 전체 화면 전환 흐름
- `src/components/`: 페이지와 주요 UI 컴포넌트
- `src/components/ops/`: 운영 화면 컴포넌트
- `src/services/`: API 호출 계층
- `vite.config.ts`: 개발 서버 프록시 설정

## 디렉토리 구조

```text
frontend/
├── package.json
├── vite.config.ts
├── public/
├── src/
│   ├── assets/             이미지와 정적 자산
│   ├── components/         사용자 화면 컴포넌트
│   │   └── ops/            운영 화면 컴포넌트
│   ├── data/               화면용 데이터 접근 래퍼
│   ├── router/             라우터 관련 코드
│   ├── services/           API 호출과 인증/세션 유틸
│   ├── App.vue             앱 진입 화면
│   ├── main.ts             부트스트랩
│   ├── index.css           전역 스타일
│   └── types.ts            공통 타입
├── dist/                   빌드 결과물
├── node_modules/           설치된 패키지
└── README.md
```

## 구조를 읽는 방법

- 전체 플로우는 `App.vue`
- 실제 화면은 `src/components/`
- API 연결은 `src/services/`
- 로컬 프록시 대상은 `vite.config.ts`

## 개발 참고

- `npm install`
- `npm run dev`
- `npm run build`
