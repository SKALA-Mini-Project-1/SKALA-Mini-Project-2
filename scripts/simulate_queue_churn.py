#!/usr/bin/env python3
"""
대기열 부하 + 랜덤 이탈(churn) 시뮬레이터.

목표:
- 프론트 하드코딩 없이 실제 대기열에 수천 명을 넣고,
- 랜덤 인원 이탈로 rank가 23명, 14명 등 불규칙하게 줄어드는 상황 재현.

사용 예시:
python3 scripts/simulate_queue_churn.py \
  --users scripts/test_users.csv \
  --concert-id 3 --schedule-id 4 \
  --viewer-email load_user_003500@test.local \
  --queue-size 3000 \
  --set-active 500 \
  --churn-min 10 --churn-max 30 \
  --tick-seconds 2 \
  --duration-seconds 300
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import List, Optional


@dataclass
class UserCred:
    email: str
    password: str


@dataclass
class Session:
    email: str
    token: str


def request_json(method: str, url: str, token: Optional[str] = None, body: Optional[dict] = None) -> dict:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url=url, method=method, headers=headers, data=data)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read().decode("utf-8")
            return json.loads(payload) if payload else {}
    except urllib.error.HTTPError as e:
        msg = e.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"HTTP {e.code} {url} {msg}") from e


def read_users(path: str) -> List[UserCred]:
    out: List[UserCred] = []
    with open(path, "r", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            out.append(UserCred(email=row["email"], password=row["password"]))
    return out


def login(base_url: str, c: UserCred) -> Session:
    d = request_json("POST", f"{base_url}/api/users/login", body={"email": c.email, "password": c.password})
    return Session(email=c.email, token=d["accessToken"])


def start_queue(base_url: str, concert_id: int, schedule_id: int, token: str) -> None:
    q = urllib.parse.urlencode({"concertId": concert_id, "scheduleId": schedule_id})
    request_json("POST", f"{base_url}/api/ticketing/start?{q}", token=token)


def leave_queue(base_url: str, concert_id: int, schedule_id: int, token: str) -> None:
    q = urllib.parse.urlencode({"concertId": concert_id, "scheduleId": schedule_id})
    request_json("POST", f"{base_url}/api/ticketing/leave?{q}", token=token)


def set_active(redis_container: str, concert_id: int, schedule_id: int, value: int) -> None:
    key = f"seat:active:concert:{concert_id}:schedule:{schedule_id}"
    subprocess.run(
        ["docker", "exec", redis_container, "redis-cli", "SET", key, str(value)],
        check=True,
        capture_output=True,
        text=True,
    )


def main() -> int:
    p = argparse.ArgumentParser(description="대기열 랜덤 이탈 시뮬레이터")
    p.add_argument("--users", required=True)
    p.add_argument("--base-url", default="http://localhost:8081")
    p.add_argument("--concert-id", type=int, required=True)
    p.add_argument("--schedule-id", type=int, required=True)
    p.add_argument("--viewer-email", required=True, help="실제 프론트에서 볼 사용자 이메일")
    p.add_argument("--queue-size", type=int, default=3000)
    p.add_argument("--workers", type=int, default=80)
    p.add_argument("--join-retries", type=int, default=4)
    p.add_argument("--join-batch-size", type=int, default=200)
    p.add_argument("--join-batch-sleep-ms", type=int, default=200)
    p.add_argument("--set-active", type=int, default=500, help="기본 500으로 막아 enter=true 방지")
    p.add_argument("--redis-container", default="concert-redis")
    p.add_argument("--churn-min", type=int, default=10)
    p.add_argument("--churn-max", type=int, default=30)
    p.add_argument("--tick-seconds", type=int, default=2)
    p.add_argument("--duration-seconds", type=int, default=300)
    args = p.parse_args()

    users = read_users(args.users)
    if len(users) < args.queue_size + 1:
        raise RuntimeError(f"users 수 부족: 필요={args.queue_size+1}, 현재={len(users)}")

    viewer = next((u for u in users if u.email == args.viewer_email), None)
    if viewer is None:
        raise RuntimeError(f"viewer-email을 users 파일에서 찾지 못했습니다: {args.viewer_email}")

    bot_users = [u for u in users if u.email != args.viewer_email][: args.queue_size]

    # 수용 인원 꽉 찬 상태로 고정해서 enter=true 제거
    set_active(args.redis_container, args.concert_id, args.schedule_id, args.set_active)
    print(f"[SETUP] active={args.set_active}")

    # 로그인
    sessions: List[Session] = []
    login_fail = 0
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = [ex.submit(login, args.base_url, u) for u in bot_users]
        for fut in as_completed(futs):
            try:
                sessions.append(fut.result())
            except Exception:
                login_fail += 1

    viewer_session = login(args.base_url, viewer)
    print(f"[SETUP] 로그인 완료 bots={len(sessions)} viewer={viewer.email}")

    # bots 먼저 큐 진입
    entered_sessions: List[Session] = []
    pending = sessions[:]
    for attempt in range(1, args.join_retries + 1):
        if not pending:
            break
        failed: List[Session] = []
        batch_size = max(1, args.join_batch_size)
        for off in range(0, len(pending), batch_size):
            batch = pending[off : off + batch_size]
            with ThreadPoolExecutor(max_workers=args.workers) as ex:
                futs = {
                    ex.submit(start_queue, args.base_url, args.concert_id, args.schedule_id, s.token): s
                    for s in batch
                }
                for fut in as_completed(futs):
                    s = futs[fut]
                    try:
                        fut.result()
                        entered_sessions.append(s)
                    except Exception:
                        failed.append(s)
            print(
                f"[SETUP] attempt={attempt} batch={off//batch_size + 1} "
                f"ok_total={len(entered_sessions)} fail_pending={len(failed)}"
            )
            if args.join_batch_sleep_ms > 0:
                time.sleep(args.join_batch_sleep_ms / 1000)
        print(f"[SETUP] bots 큐 진입 시도 {attempt}/{args.join_retries}: success={len(pending)-len(failed)}, fail={len(failed)}")
        pending = failed
        if pending:
            time.sleep(1)
    print(f"[SETUP] bots 큐 진입 완료: success={len(entered_sessions)}, fail={len(pending)}, login_fail={login_fail}")

    # viewer 마지막 진입 => 초기 rank ~= queue-size+1
    for _ in range(3):
        try:
            start_queue(args.base_url, args.concert_id, args.schedule_id, viewer_session.token)
            break
        except Exception:
            time.sleep(1)
    else:
        raise RuntimeError("viewer start_queue 실패 (3회 재시도)")
    print(f"[SETUP] viewer 큐 진입 완료: {viewer.email}")
    print("[RUN] 프론트에서 viewer 계정으로 /concert/queue 화면을 보고 rank 감소를 캡처하세요.")

    alive = entered_sessions[:]
    end_at = time.time() + args.duration_seconds
    tick = 0
    while time.time() < end_at and alive:
        tick += 1
        n = random.randint(args.churn_min, args.churn_max)
        n = min(n, len(alive))
        victims = random.sample(alive, n)

        with ThreadPoolExecutor(max_workers=min(args.workers, n)) as ex:
            futs = [
                ex.submit(leave_queue, args.base_url, args.concert_id, args.schedule_id, s.token)
                for s in victims
            ]
            for fut in as_completed(futs):
                try:
                    fut.result()
                except Exception:
                    pass

        victim_emails = {v.email for v in victims}
        alive = [s for s in alive if s.email not in victim_emails]
        print(f"[TICK {tick}] leave={n}, alive={len(alive)}")
        time.sleep(args.tick_seconds)

    print("[DONE] 시뮬레이션 종료")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
