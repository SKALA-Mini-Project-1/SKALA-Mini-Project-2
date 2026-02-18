# Ticketing Service

<!-- Redis 실행 방법 -->
cd infra
docker-compose up -d

## 대기열 테스트 스크립트

### 1) 테스트 사용자 생성
```bash
./scripts/seed_test_users.sh 2000
```

### 2) 실시간 메트릭 관측
```bash
./scripts/watch_queue_metrics.sh 3 4 1
```

### 3) 대기열 부하 테스트
```bash
python3 scripts/queue_test_runner.py load \
  --users scripts/test_users.csv \
  --concert-id 3 --schedule-id 4 \
  --set-active 500 \
  --workers 200 \
  --output-json scripts/load_result.json
```

### 4) 팬 우선순위 테스트
```bash
python3 scripts/queue_test_runner.py fan \
  --users scripts/test_users.csv \
  --concert-id 3 --schedule-id 4 \
  --set-active 500 \
  --sample-size 200
```

### 5) active 임계값 테스트
```bash
python3 scripts/queue_test_runner.py active \
  --users scripts/test_users.csv \
  --concert-id 3 --schedule-id 4 \
  --capacity 500
```
