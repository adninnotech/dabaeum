#!/usr/bin/env python3
"""가상 데이터 생성·삭제 — 로컬 DB 전용 (docs/specs/course-recommendation.md §12.8).

회원 2,000명·강좌 200개·신청 20,000건과 찜·노출을 만든다. 의도한 상관을 심는다:
  60대 이상 ↔ 인문교양(TSIX_05), 20~30대 ↔ 직업능력(TSIX_03), 회원 관심 분야 ↔ 그 대분류 강좌,
  여성 ↔ 생활문화예술(TSIX_0402, 약하게), 유료 강좌 기피, 회원마다 선호 대분류 하나(반복 신청).
관심 분야(약 70% 의 회원, 1~2개)는 신청 가중치에도 쓰여 interest_match 가 정답을 미리 아는 값에 가깝다.
따라서 이 데이터로 잰 성능 수치는 부풀려져 있다. 실제 성능으로 해석하지 않는다.
표식: 이메일 `@synthetic.invalid`, 기관 코드·강좌 코드 접두어 `SYN-`. `--purge` 는 표식이 붙은 행만 지운다.
DB 이름이 dabaeum_dev·dabaeum_prd 이거나 알 수 없으면 아무것도 쓰지 않고 종료 코드 2 로 끝낸다.

사용: python synthetic.py [--seed 7]   /   python synthetic.py --purge
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402

from recommend import db, gate  # noqa: E402

LOG = logging.getLogger("synthetic")
EMAIL_DOMAIN = "@synthetic.invalid"
CODE_PREFIX = "SYN-"
N_USERS, N_COURSES, N_ENROLLMENTS = 2000, 200, 20000
N_RECRUITING = 60
INTERESTS_PER_USER, IMPRESSIONS_PER_USER = 2, 6
INTEREST_RATE = 0.7        # 관심 분야를 입력한 회원 비율(선택 입력이라 없는 회원이 있다)
INTEREST_WEIGHT = 4.0      # 관심 분야 대분류 강좌의 신청 가중치 배수
REGIONS = ("11", "14", "17", "20", "23", "26", "29", "71", "72")  # 기관 소재지(tb_institutions.region)용
DISTRICTS = {"11": "중구", "14": "동구", "17": "서구", "20": "남구", "23": "북구", "26": "수성구",
             "29": "달서구", "71": "달성군", "72": "군위군"}
# V41 이 넣은 소분류 코드(기존 표준). 새 분류는 만들지 않는다.
CLASSIFICATIONS = (
    "TSIX_010201", "TSIX_010202", "TSIX_030101", "TSIX_030103", "TSIX_030201", "TSIX_030304",
    "TSIX_040101", "TSIX_040102", "TSIX_040103", "TSIX_040201", "TSIX_040203", "TSIX_040301", "TSIX_040302",
    "TSIX_050101", "TSIX_050204", "TSIX_050301", "TSIX_050302", "TSIX_050303", "TSIX_060102", "TSIX_060301",
)
# 큰 기관 6곳(기관 feature 로 남는다) + 작은 기관 6곳(강좌 20개 미만 → OTHER).
INSTITUTION_SIZES = (30, 30, 30, 30, 30, 25, 5, 5, 5, 4, 3, 3)


@dataclass(frozen=True)
class SyntheticData:
    institutions: pd.DataFrame
    users: pd.DataFrame
    roles: pd.DataFrame
    courses: pd.DataFrame
    sessions: pd.DataFrame
    enrollments: pd.DataFrame
    completions: pd.DataFrame
    interests: pd.DataFrame
    impressions: pd.DataFrame
    user_interests: pd.DataFrame
    agreements: pd.DataFrame


def _ids(n: int) -> list[str]:
    return [str(uuid.uuid4()) for _ in range(n)]


def _institutions(rng) -> pd.DataFrame:
    n = len(INSTITUTION_SIZES)
    regions = rng.choice(REGIONS, size=n)
    return pd.DataFrame({"id": _ids(n), "institution_code": [f"{CODE_PREFIX}INST-{i + 1:02d}" for i in range(n)],
                         "name": [f"가상기관 {i + 1:02d}" for i in range(n)], "status": "ACTIVE",
                         "region": [f"대구광역시 {DISTRICTS[r]}" for r in regions]})


def _users(rng, now: datetime) -> pd.DataFrame:
    ages = rng.integers(20, 80, size=N_USERS)
    births = [(now - timedelta(days=int(a * 365.25 + rng.integers(0, 365)))).date() for a in ages]
    created = [now - timedelta(days=int(d)) for d in rng.integers(400, 900, size=N_USERS)]
    return pd.DataFrame({"id": _ids(N_USERS), "name": [f"가상회원{i + 1:05d}" for i in range(N_USERS)],
                         "email": [f"syn-{i + 1:05d}{EMAIL_DOMAIN}" for i in range(N_USERS)],
                         "birth_date": births, "age": ages,
                         "gender": rng.choice(["MALE", "FEMALE"], size=N_USERS),
                         "interest_majors": _interest_majors(rng),
                         "status": "ACTIVE", "created_at": created})


def _interest_majors(rng) -> list[tuple]:
    """회원마다 관심 대분류(TSIX_0N) 0~2개. 입력하지 않은 회원은 빈 튜플."""
    majors = sorted({code[:7] for code in CLASSIFICATIONS})
    picked = []
    for has in rng.random(N_USERS) < INTEREST_RATE:
        count = int(rng.integers(1, 3)) if has else 0
        picked.append(tuple(sorted(rng.choice(majors, size=count, replace=False))))
    return picked


def _user_interests(users: pd.DataFrame, now: datetime) -> tuple[pd.DataFrame, pd.DataFrame]:
    """tb_user_interests 행(분류 1단계 코드 TSIX_0N0000)과 INTEREST 동의 기록. 동의는 관심 분야를 넣은 회원에게만."""
    rows, agreements = [], []
    for user_id, majors, created in zip(users["id"], users["interest_majors"], users["created_at"]):
        if not majors:
            continue
        rows.extend((user_id, f"{major}0000", created) for major in majors)
        agreements.append((str(uuid.uuid4()), user_id, "INTEREST", True, created))
    return (pd.DataFrame(rows, columns=["user_id", "classification_code", "created_at"]),
            pd.DataFrame(agreements, columns=["id", "user_id", "agreement_type", "agreed", "agreed_at"]))


def _courses(rng, institutions: pd.DataFrame, today) -> pd.DataFrame:
    owner = np.repeat(institutions["id"].to_numpy(), INSTITUTION_SIZES)
    recruiting = np.zeros(N_COURSES, dtype=bool)
    recruiting[rng.choice(N_COURSES, size=N_RECRUITING, replace=False)] = True
    starts = [today - timedelta(days=int(rng.integers(5, 25))) if r else today - timedelta(days=int(rng.integers(40, 360)))
              for r in recruiting]
    ends = [s + timedelta(days=int(rng.integers(30, 50))) if r else s + timedelta(days=30)
            for s, r in zip(starts, recruiting)]
    begin = [e + timedelta(days=7) for e in ends]
    finish = [b + timedelta(days=60) for b in begin]
    paid = rng.random(N_COURSES) < 0.3
    codes = rng.choice(CLASSIFICATIONS, size=N_COURSES)
    status = ["RECRUITING" if r else ("COMPLETED" if f < today else "IN_PROGRESS") for r, f in zip(recruiting, finish)]
    audiences = rng.choice(["ANYONE", "ADULTS", "MIDDLE_AGED_SENIORS", "ANYONE,WOMEN", "CHILDREN"],
                           size=N_COURSES, p=[0.55, 0.2, 0.15, 0.07, 0.03])
    return pd.DataFrame({
        "id": _ids(N_COURSES), "institution_id": owner, "course_code": [f"{CODE_PREFIX}C-{i + 1:04d}" for i in range(N_COURSES)],
        "title": [f"가상강좌 {i + 1:04d}" for i in range(N_COURSES)],
        "education_type": rng.choice(["OFFLINE", "ONLINE", "HYBRID"], size=N_COURSES, p=[0.6, 0.3, 0.1]),
        "start_date": begin, "end_date": finish, "recruit_start_date": starts, "recruit_end_date": ends,
        "capacity": 1000, "waitlist_capacity": 0, "status": status,
        "tuition_type": np.where(paid, "PAID", "FREE"),
        "tuition_amount": np.where(paid, rng.integers(1, 10, size=N_COURSES) * 10000, None),
        "material_fee_type": "FREE", "target_audiences": audiences,
        "credit_bank_eligible": rng.random(N_COURSES) < 0.1, "classification_code": codes,
        "created_at": [datetime.combine(s - timedelta(days=3), datetime.min.time(), timezone.utc) for s in starts],
    })


def _sessions(rng, courses: pd.DataFrame) -> pd.DataFrame:
    rows = []
    for course_id, begin in zip(courses["id"], courses["start_date"]):
        hour = int(rng.choice([10, 14, 19]))
        first = datetime.combine(begin, datetime.min.time(), timezone.utc) + timedelta(hours=hour - 9)
        for no in range(1, int(rng.integers(4, 13)) + 1):
            at = first + timedelta(days=7 * (no - 1))
            rows.append((str(uuid.uuid4()), course_id, no, at, at + timedelta(hours=2), "SCHEDULED"))
    return pd.DataFrame(rows, columns=["id", "course_id", "session_no", "starts_at", "ends_at", "status"])


def affinity(users: pd.DataFrame, courses: pd.DataFrame, rng) -> np.ndarray:
    """회원 × 강좌 선택 가중치. 심은 상관이 여기 있다."""
    major = courses["classification_code"].str[:7].to_numpy()
    middle = courses["classification_code"].str[:9].to_numpy()
    age = users["age"].to_numpy()[:, None]
    weight = np.ones((len(users), len(courses)))
    weight *= np.where((age >= 60) & (major == "TSIX_05")[None, :], 6.0, 1.0)
    weight *= np.where((age < 40) & (major == "TSIX_03")[None, :], 5.0, 1.0)
    interested = np.array([[m in majors for m in major] for majors in users["interest_majors"]])
    weight *= np.where(interested, INTEREST_WEIGHT, 1.0)
    weight *= np.where((users["gender"].to_numpy()[:, None] == "FEMALE") & (middle == "TSIX_0402")[None, :], 2.0, 1.0)
    weight *= np.where((courses["tuition_type"].to_numpy() == "PAID")[None, :], 0.5, 1.0)
    favourite = rng.choice(sorted(set(major)), size=len(users))
    weight *= np.where(favourite[:, None] == major[None, :], 3.0, 1.0)
    return weight * rng.lognormal(0, 0.5, size=len(courses))[None, :]


def _random_time(rng, start, end, now: datetime) -> datetime:
    lo = datetime.combine(start, datetime.min.time(), timezone.utc)
    hi = min(datetime.combine(end, datetime.min.time(), timezone.utc) + timedelta(days=1), now)
    return lo + timedelta(seconds=float(rng.uniform(0, max((hi - lo).total_seconds(), 1))))


def _enrollments(rng, users, courses, weight, now):
    per_user = rng.multinomial(N_ENROLLMENTS, np.full(len(users), 1 / len(users)))
    per_user = np.minimum(per_user, len(courses) // 2)
    rows, completions = [], []
    for u, count in enumerate(per_user):
        probs = weight[u] / weight[u].sum()
        for c in rng.choice(len(courses), size=int(count), replace=False, p=probs):
            course = courses.iloc[c]
            at = _random_time(rng, course["recruit_start_date"], course["recruit_end_date"], now)
            recruiting = course["status"] == "RECRUITING"
            status = "APPLIED" if recruiting else ("CANCELLED" if rng.random() < 0.05 else "APPROVED")
            enrollment_id = str(uuid.uuid4())
            rows.append((enrollment_id, course["id"], users["id"].iat[u], status, at,
                         at + timedelta(days=1) if status == "APPROVED" else None,
                         at + timedelta(days=2) if status == "CANCELLED" else None))
            if status == "APPROVED" and course["status"] == "COMPLETED" and rng.random() < 0.6:
                done = datetime.combine(course["end_date"], datetime.min.time(), timezone.utc)
                completions.append((str(uuid.uuid4()), enrollment_id, "COMPLETED", 90, 600, done))
    enrollments = pd.DataFrame(rows, columns=["id", "course_id", "user_id", "status", "applied_at", "approved_at",
                                              "cancelled_at"])
    completed = pd.DataFrame(completions, columns=["id", "enrollment_id", "status", "attendance_rate",
                                                   "completed_minutes", "completed_at"])
    return enrollments, completed


def _side_signals(rng, users, courses, weight, enrollments, now):
    taken = set(zip(enrollments["user_id"], enrollments["course_id"]))
    interests, impressions = [], []
    for u, user_id in enumerate(users["id"]):
        free = [c for c in range(len(courses)) if (user_id, courses["id"].iat[c]) not in taken]
        probs = weight[u, free] / weight[u, free].sum()
        for c in rng.choice(free, size=INTERESTS_PER_USER, replace=False, p=probs):
            interests.append((str(uuid.uuid4()), user_id, courses["id"].iat[c],
                              now - timedelta(days=float(rng.uniform(1, 300)))))
        shown_at = now - timedelta(days=float(rng.uniform(1, 60)))
        for position, c in enumerate(rng.choice(free, size=IMPRESSIONS_PER_USER, replace=False), start=1):
            impressions.append((str(uuid.uuid4()), user_id, courses["id"].iat[c], "PERSONAL",
                                "SIMILAR_CLASSIFICATION", 0, position, shown_at))
    return (pd.DataFrame(interests, columns=["id", "user_id", "course_id", "created_at"]),
            pd.DataFrame(impressions, columns=["id", "user_id", "course_id", "basis", "reason_type", "match_level",
                                               "position", "shown_at"]))


def generate(seed: int, now: datetime) -> SyntheticData:
    rng = np.random.default_rng(seed)
    today = now.astimezone(timezone(timedelta(hours=9))).date()
    institutions = _institutions(rng)
    users = _users(rng, now)
    courses = _courses(rng, institutions, today)
    weight = affinity(users, courses, rng)
    enrollments, completions = _enrollments(rng, users, courses, weight, now)
    interests, impressions = _side_signals(rng, users, courses, weight, enrollments, now)
    roles = pd.DataFrame({"id": _ids(len(users)), "user_id": users["id"], "role": "LEARNER", "created_at": now})
    user_interests, agreements = _user_interests(users, now)
    return SyntheticData(institutions, users, roles, courses, _sessions(rng, courses), enrollments, completions,
                         interests, impressions, user_interests, agreements)


# (테이블, 열) — 부모부터 넣는다. 지역·학번 컬럼은 넣지 않는다(옛 스키마에서는 기본값·트리거가 채운다).
INSERT_ORDER = (
    ("tb_institutions", "institutions", ("id", "institution_code", "name", "status", "region")),
    ("tb_users", "users", ("id", "name", "email", "birth_date", "gender", "status", "created_at")),
    ("tb_user_roles", "roles", ("id", "user_id", "role", "created_at")),
    ("tb_user_interests", "user_interests", ("user_id", "classification_code", "created_at")),
    ("tb_user_agreements", "agreements", ("id", "user_id", "agreement_type", "agreed", "agreed_at")),
    ("tb_courses", "courses", ("id", "institution_id", "course_code", "title", "education_type", "start_date",
                               "end_date", "recruit_start_date", "recruit_end_date", "capacity", "waitlist_capacity",
                               "status", "tuition_type", "tuition_amount", "material_fee_type", "target_audiences",
                               "credit_bank_eligible", "classification_code", "created_at")),
    ("tb_course_sessions", "sessions", ("id", "course_id", "session_no", "starts_at", "ends_at", "status")),
    ("tb_enrollments", "enrollments", ("id", "course_id", "user_id", "status", "applied_at", "approved_at",
                                       "cancelled_at")),
    ("tb_completions", "completions", ("id", "enrollment_id", "status", "attendance_rate", "completed_minutes",
                                       "completed_at")),
    ("tb_course_interests", "interests", ("id", "user_id", "course_id", "created_at")),
    ("tb_recommendation_impressions", "impressions", ("id", "user_id", "course_id", "basis", "reason_type",
                                                      "match_level", "position", "shown_at")),
)

_MARKED_USERS = f"SELECT id FROM tb_users WHERE email LIKE '%{EMAIL_DOMAIN}'"
_MARKED_INSTITUTIONS = f"SELECT id FROM tb_institutions WHERE institution_code LIKE '{CODE_PREFIX}%'"
_MARKED_COURSES = (f"SELECT id FROM tb_courses WHERE course_code LIKE '{CODE_PREFIX}%' "
                   f"AND institution_id IN ({_MARKED_INSTITUTIONS})")
_MARKED_ENROLLMENTS = (f"SELECT id FROM tb_enrollments WHERE user_id IN ({_MARKED_USERS}) "
                       f"OR course_id IN ({_MARKED_COURSES})")
# 자식부터 지운다(FK RESTRICT). 점수·노출·인기·조회는 CASCADE 지만 명시적으로 지운다.
PURGE_STATEMENTS = (
    f"DELETE FROM tb_completions WHERE enrollment_id IN ({_MARKED_ENROLLMENTS})",
    f"DELETE FROM tb_enrollments WHERE id IN ({_MARKED_ENROLLMENTS})",
    f"DELETE FROM tb_course_interests WHERE user_id IN ({_MARKED_USERS}) OR course_id IN ({_MARKED_COURSES})",
    f"DELETE FROM tb_recommendation_impressions WHERE user_id IN ({_MARKED_USERS}) OR course_id IN ({_MARKED_COURSES})",
    f"DELETE FROM tb_course_recommendation_scores WHERE user_id IN ({_MARKED_USERS}) OR course_id IN ({_MARKED_COURSES})",
    f"DELETE FROM tb_course_popularity WHERE course_id IN ({_MARKED_COURSES})",
    f"DELETE FROM tb_course_views WHERE course_id IN ({_MARKED_COURSES})",
    f"DELETE FROM tb_course_sessions WHERE course_id IN ({_MARKED_COURSES})",
    f"DELETE FROM tb_courses WHERE id IN ({_MARKED_COURSES})",
    f"DELETE FROM tb_user_interests WHERE user_id IN ({_MARKED_USERS})",
    f"DELETE FROM tb_user_agreements WHERE user_id IN ({_MARKED_USERS})",
    f"DELETE FROM tb_user_roles WHERE user_id IN ({_MARKED_USERS})",
    f"DELETE FROM tb_users WHERE id IN ({_MARKED_USERS})",
    f"DELETE FROM tb_institutions WHERE id IN ({_MARKED_INSTITUTIONS})",
)


def _value(v):
    return None if v is None or (not isinstance(v, str) and pd.isna(v)) else v


def insert(conn, data: SyntheticData) -> None:
    with conn.transaction(), conn.cursor() as cur:
        for table, attr, columns in INSERT_ORDER:
            frame = getattr(data, attr)
            with cur.copy(f"COPY {table} ({', '.join(columns)}) FROM STDIN") as copy:
                for row in frame[list(columns)].itertuples(index=False, name=None):
                    copy.write_row(tuple(_value(v) for v in row))
            LOG.info("%s: %d행", table, len(frame))


def purge(conn) -> None:
    with conn.transaction(), conn.cursor() as cur:
        for statement in PURGE_STATEMENTS:
            cur.execute(statement)
            LOG.info("%s: %d행 삭제", statement.split()[2], cur.rowcount)


def marked_count(conn) -> int:
    with conn.cursor() as cur:
        cur.execute(f"SELECT (SELECT count(*) FROM ({_MARKED_USERS}) u) + (SELECT count(*) FROM ({_MARKED_INSTITUTIONS}) i)")
        return int(cur.fetchone()[0])


def main(argv=None) -> int:
    logging.basicConfig(stream=sys.stderr, level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    parser = argparse.ArgumentParser(description="가상 데이터 생성/삭제 (로컬 DB 전용)")
    parser.add_argument("--purge", action="store_true", help="표식이 붙은 가상 데이터만 지운다")
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args(argv)
    try:
        settings = db.settings_from_env(os.environ)
    except db.MissingDatabaseSettings as error:
        LOG.error("%s", error)
        return gate.EXIT_ERROR
    if gate.is_forbidden_database(settings.name):
        LOG.error("가상 데이터는 로컬 DB 전용입니다. dev·prd DB 에는 쓰지 않습니다")
        return gate.EXIT_REFUSED
    try:
        with db.connect(settings) as conn:
            if gate.is_forbidden_database(db.current_database(conn)):
                LOG.error("접속한 DB 가 dev·prd 입니다. 아무것도 쓰지 않고 끝냅니다")
                return gate.EXIT_REFUSED
            if args.purge:
                purge(conn)
                return gate.EXIT_TRAINED
            if marked_count(conn) > 0:
                LOG.error("이미 가상 데이터가 있습니다. 먼저 --purge 로 지우세요")
                return gate.EXIT_ERROR
            insert(conn, generate(args.seed, datetime.now(timezone.utc)))
            return gate.EXIT_TRAINED
    except Exception as error:
        LOG.error("가상 데이터 작업 실패: %s", db.redact(f"{type(error).__name__}: {error}", settings))
        return gate.EXIT_ERROR


if __name__ == "__main__":
    sys.exit(main())
