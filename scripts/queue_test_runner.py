#!/usr/bin/env python3
"""
대기열/팬우선순위/active 검증용 통합 스크립트.

사용 예시:
1) 대기열 부하 테스트
   python3 scripts/queue_test_runner.py load \
     --users scripts/test_users.csv \
     --concert-id 3 --schedule-id 4 \
     --set-active 500 --workers 200

2) 팬 우선순위 비교 테스트
   python3 scripts/queue_test_runner.py fan \
     --users scripts/test_users.csv \
     --concert-id 3 --schedule-id 4 \
     --set-active 500 --sample-size 200

3) active 임계값 테스트
   python3 scripts/queue_test_runner.py active \
     --users scripts/test_users.csv \
     --concert-id 3 --schedule-id 4 \
     --capacity 500
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple


@dataclass
class UserCred:
    email: str
    password: str
    fan_score: Optional[int] = None


@dataclass
class Session:
    email: str
    token: str
    user_id: int
    fan_score: Optional[int]


def read_users(path: str) -> List[UserCred]:
    users: List[UserCred] = []
    with open(path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            fs = row.get("fan_score")
            fan_score = int(fs) if fs not in (None, "", "null") else None
            users.append(UserCred(email=row["email"], password=row["password"], fan_score=fan_score))
    if not users:
        raise RuntimeError(f"사용자 파일이 비어있습니다: {path}")
    return users


def request_json(
    method: str,
    url: str,
    token: Optional[str] = None,
    body: Optional[dict] = None,
    timeout: float = 30.0,
) -> dict:
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url=url, method=method, headers=headers, data=data)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        msg = e.read().decode("utf-8", errors="ignore")
        raise RuntimeError(f"HTTP {e.code} {url} {msg}") from e


def login(base_url: str, cred: UserCred) -> Session:
    url = f"{base_url.rstrip('/')}/api/users/login"
    body = {"email": cred.email, "password": cred.password}
    d = request_json("POST", url, body=body)
    return Session(
        email=cred.email,
        token=d["accessToken"],
        user_id=int(d["userId"]),
        fan_score=cred.fan_score,
    )


def ticketing_start(base_url: str, concert_id: int, schedule_id: int, token: str) -> dict:
    q = urllib.parse.urlencode({"concertId": concert_id, "scheduleId": schedule_id})
    url = f"{base_url.rstrip('/')}/api/ticketing/start?{q}"
    return request_json("POST", url, token=token)


def ticketing_status(base_url: str, concert_id: int, schedule_id: int, token: str) -> dict:
    q = urllib.parse.urlencode({"concertId": concert_id, "scheduleId": schedule_id})
    url = f"{base_url.rstrip('/')}/api/ticketing/status?{q}"
    return request_json("GET", url, token=token)


def redis_set_active(redis_container: str, concert_id: int, schedule_id: int, value: int) -> None:
    key = f"seat:active:concert:{concert_id}:schedule:{schedule_id}"
    subprocess.run(
        ["docker", "exec", redis_container, "redis-cli", "SET", key, str(value)],
        check=True,
        capture_output=True,
        text=True,
    )


def redis_get_active(redis_container: str, concert_id: int, schedule_id: int) -> int:
    key = f"seat:active:concert:{concert_id}:schedule:{schedule_id}"
    out = subprocess.run(
        ["docker", "exec", redis_container, "redis-cli", "GET", key],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    if out == "":
        return 0
    return int(out)


def summarize_ranks(rows: List[dict]) -> dict:
    ranks = [r["rank"] for r in rows if isinstance(r.get("rank"), int)]
    enters = sum(1 for r in rows if r.get("enter") is True)
    waiting = sum(1 for r in rows if r.get("enter") is not True)
    result = {
        "count": len(rows),
        "enter_count": enters,
        "waiting_count": waiting,
        "rank_count": len(ranks),
    }
    if ranks:
        result.update(
            {
                "rank_min": min(ranks),
                "rank_max": max(ranks),
                "rank_avg": round(sum(ranks) / len(ranks), 2),
                "rank_median": statistics.median(ranks),
            }
        )
    return result


def run_load(args: argparse.Namespace) -> int:
    users = read_users(args.users)
    if args.limit:
        users = users[: args.limit]

    if args.set_active is not None:
        redis_set_active(args.redis_container, args.concert_id, args.schedule_id, args.set_active)

    sessions: List[Session] = []
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futures = [ex.submit(login, args.base_url, u) for u in users]
        for fut in as_completed(futures):
            sessions.append(fut.result())

    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futures = [
            ex.submit(ticketing_start, args.base_url, args.concert_id, args.schedule_id, s.token)
            for s in sessions
        ]
        for fut in as_completed(futures):
            _ = fut.result()

    time.sleep(args.status_delay)
    results: List[dict] = []
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futures = {
            ex.submit(ticketing_status, args.base_url, args.concert_id, args.schedule_id, s.token): s
            for s in sessions
        }
        for fut in as_completed(futures):
            s = futures[fut]
            r = fut.result()
            results.append(
                {
                    "email": s.email,
                    "user_id": s.user_id,
                    "fan_score": s.fan_score,
                    "enter": r.get("enter"),
                    "rank": r.get("rank"),
                }
            )

    summary = summarize_ranks(results)
    active_now = redis_get_active(args.redis_container, args.concert_id, args.schedule_id)
    print("[LOAD] summary")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"[LOAD] redis active={active_now}")

    if args.output_json:
        with open(args.output_json, "w", encoding="utf-8") as f:
            json.dump({"summary": summary, "rows": results}, f, ensure_ascii=False, indent=2)
        print(f"[LOAD] 결과 저장: {args.output_json}")
    return 0


def run_fan(args: argparse.Namespace) -> int:
    users = read_users(args.users)
    low = [u for u in users if (u.fan_score or 0) <= args.low_max]
    high = [u for u in users if (u.fan_score or 0) >= args.high_min]
    if len(low) < args.sample_size or len(high) < args.sample_size:
        raise RuntimeError(
            f"샘플 수 부족: low={len(low)} high={len(high)} 필요={args.sample_size}"
        )

    low = low[: args.sample_size]
    high = high[: args.sample_size]
    cohort = low + high
    random.shuffle(cohort)

    if args.set_active is not None:
        redis_set_active(args.redis_container, args.concert_id, args.schedule_id, args.set_active)

    sessions: List[Session] = []
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futures = [ex.submit(login, args.base_url, u) for u in cohort]
        for fut in as_completed(futures):
            sessions.append(fut.result())

    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futures = [
            ex.submit(ticketing_start, args.base_url, args.concert_id, args.schedule_id, s.token)
            for s in sessions
        ]
        for fut in as_completed(futures):
            _ = fut.result()

    time.sleep(args.status_delay)
    rows: List[dict] = []
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        futures = {
            ex.submit(ticketing_status, args.base_url, args.concert_id, args.schedule_id, s.token): s
            for s in sessions
        }
        for fut in as_completed(futures):
            s = futures[fut]
            st = fut.result()
            rows.append(
                {
                    "email": s.email,
                    "fan_score": s.fan_score or 0,
                    "rank": st.get("rank"),
                    "enter": st.get("enter"),
                }
            )

    low_ranks = [r["rank"] for r in rows if (r["fan_score"] <= args.low_max and isinstance(r["rank"], int))]
    high_ranks = [r["rank"] for r in rows if (r["fan_score"] >= args.high_min and isinstance(r["rank"], int))]
    print("[FAN] low ranks:", len(low_ranks), "high ranks:", len(high_ranks))
    if not low_ranks or not high_ranks:
        raise RuntimeError("rank 데이터를 충분히 수집하지 못했습니다. active 값을 높여 enter를 막고 다시 시도하세요.")

    low_avg = sum(low_ranks) / len(low_ranks)
    high_avg = sum(high_ranks) / len(high_ranks)
    print(
        json.dumps(
            {
                "low_avg_rank": round(low_avg, 2),
                "high_avg_rank": round(high_avg, 2),
                "low_median_rank": statistics.median(low_ranks),
                "high_median_rank": statistics.median(high_ranks),
                "priority_applied": high_avg < low_avg,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return 0


def run_active(args: argparse.Namespace) -> int:
    users = read_users(args.users)
    if not users:
        raise RuntimeError("users 파일에 유저가 없습니다.")
    u = users[0]
    s = login(args.base_url, u)

    # 테스트 유저를 먼저 큐에 넣는다.
    ticketing_start(args.base_url, args.concert_id, args.schedule_id, s.token)

    # active = capacity -> 입장 불가 기대
    redis_set_active(args.redis_container, args.concert_id, args.schedule_id, args.capacity)
    st1 = ticketing_status(args.base_url, args.concert_id, args.schedule_id, s.token)

    # active = capacity-1 -> 입장 가능 기대
    redis_set_active(args.redis_container, args.concert_id, args.schedule_id, args.capacity - 1)
    st2 = ticketing_status(args.base_url, args.concert_id, args.schedule_id, s.token)

    result = {
        "case_capacity_full": st1,
        "case_capacity_minus_one": st2,
        "active_rule_ok": (st1.get("enter") is False and st2.get("enter") is True),
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="대기열 테스트 러너")
    p.add_argument("--base-url", default="http://localhost:8081", help="백엔드 API 주소")
    p.add_argument("--redis-container", default="concert-redis", help="Redis 컨테이너 이름")

    sub = p.add_subparsers(dest="cmd", required=True)

    load = sub.add_parser("load", help="대기열 부하 테스트")
    load.add_argument("--users", required=True, help="CSV 파일(email,password,fan_score)")
    load.add_argument("--concert-id", type=int, required=True)
    load.add_argument("--schedule-id", type=int, required=True)
    load.add_argument("--workers", type=int, default=100)
    load.add_argument("--limit", type=int, default=0, help="상위 N명만 사용 (0이면 전체)")
    load.add_argument("--status-delay", type=float, default=1.0)
    load.add_argument("--set-active", type=int, default=None, help="테스트 전에 active 강제 세팅")
    load.add_argument("--output-json", default="", help="상세 결과 저장 경로")

    fan = sub.add_parser("fan", help="팬 우선순위 검증")
    fan.add_argument("--users", required=True, help="CSV 파일(email,password,fan_score)")
    fan.add_argument("--concert-id", type=int, required=True)
    fan.add_argument("--schedule-id", type=int, required=True)
    fan.add_argument("--workers", type=int, default=100)
    fan.add_argument("--sample-size", type=int, default=100)
    fan.add_argument("--low-max", type=int, default=0)
    fan.add_argument("--high-min", type=int, default=100)
    fan.add_argument("--status-delay", type=float, default=1.0)
    fan.add_argument("--set-active", type=int, default=500, help="rank 비교를 위해 active를 높게 유지")

    active = sub.add_parser("active", help="active 임계값 검증")
    active.add_argument("--users", required=True, help="CSV 파일(email,password,fan_score)")
    active.add_argument("--concert-id", type=int, required=True)
    active.add_argument("--schedule-id", type=int, required=True)
    active.add_argument("--capacity", type=int, default=500)
    return p


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    if getattr(args, "limit", 0) == 0:
        args.limit = None
    if getattr(args, "output_json", "") == "":
        args.output_json = None

    try:
        if args.cmd == "load":
            return run_load(args)
        if args.cmd == "fan":
            return run_fan(args)
        if args.cmd == "active":
            return run_active(args)
        raise RuntimeError(f"알 수 없는 명령: {args.cmd}")
    except Exception as e:
        print(f"[ERROR] {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
