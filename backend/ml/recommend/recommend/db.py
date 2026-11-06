"""DB 접속(환경변수만), 읽기 SQL, 쓰기(run 행, 점수 교체 트랜잭션).

- 자격은 DABAEUM_DB_HOST/PORT/NAME/USERNAME/PASSWORD 로만 받는다. 값은 로그·메시지·리포트에 쓰지 않는다.
- 회원은 id 와 파생 속성(생년월일→연령대, 성별, 가입일)과 관심 분야 분류 코드만 읽는다. 이름·이메일·전화는 읽지 않는다(§12.7).
- 쓰기는 tb_recommendation_model_runs 와 tb_course_recommendation_scores 뿐이다(00-common §3).
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal

import pandas as pd

ENV_NAMES = ("DABAEUM_DB_HOST", "DABAEUM_DB_PORT", "DABAEUM_DB_NAME", "DABAEUM_DB_USERNAME", "DABAEUM_DB_PASSWORD")
APPLICATION_NAME = "dabaeum-recommend-model"
MESSAGE_LIMIT = 500
REPORT_PATH_LIMIT = 300


class MissingDatabaseSettings(RuntimeError):
    """환경변수가 없거나 틀렸다. 메시지에는 변수 이름만 담는다."""


@dataclass(frozen=True)
class DatabaseSettings:
    host: str
    port: int
    name: str
    username: str
    password: str

    def __repr__(self) -> str:  # 실수로 로그에 찍혀도 값이 나가지 않게 한다
        return "DatabaseSettings(***)"


@dataclass(frozen=True)
class RawData:
    users: pd.DataFrame
    courses: pd.DataFrame
    enrollments: pd.DataFrame
    interests: pd.DataFrame
    impressions: pd.DataFrame
    user_interests: pd.DataFrame  # 회원 관심 분야(tb_user_interests). 없는 회원이 있다


def settings_from_env(env) -> DatabaseSettings:
    missing = [name for name in ENV_NAMES if not str(env.get(name, "")).strip()]
    if missing:
        raise MissingDatabaseSettings("DB 환경변수가 없습니다: " + ", ".join(missing))
    port = str(env["DABAEUM_DB_PORT"]).strip()
    if not port.isdigit():
        raise MissingDatabaseSettings("DABAEUM_DB_PORT 가 숫자가 아닙니다")
    return DatabaseSettings(env["DABAEUM_DB_HOST"].strip(), int(port), env["DABAEUM_DB_NAME"].strip(),
                            env["DABAEUM_DB_USERNAME"].strip(), env["DABAEUM_DB_PASSWORD"])


def redact(message: str, settings: DatabaseSettings | None, limit: int = MESSAGE_LIMIT) -> str:
    """메시지에서 자격 값을 가린다. run 행 message 와 로그에 쓰기 전에 거친다."""
    text = str(message)
    if settings is not None:
        for secret in (settings.password, settings.username, settings.host):
            if secret:
                text = text.replace(secret, "***")
    return text[:limit]


def connect(settings: DatabaseSettings):
    import psycopg

    return psycopg.connect(host=settings.host, port=settings.port, dbname=settings.name, user=settings.username,
                           password=settings.password, application_name=APPLICATION_NAME, connect_timeout=15,
                           autocommit=True)  # 트랜잭션은 conn.transaction() 블록으로만 연다


def current_database(conn) -> str:
    with conn.cursor() as cur:
        cur.execute("SELECT current_database()")
        return cur.fetchone()[0]


USERS_SQL = """
SELECT u.id::text AS user_id, u.birth_date, u.gender, u.created_at
  FROM tb_users u
 WHERE u.status = 'ACTIVE'
   AND EXISTS (SELECT 1 FROM tb_user_roles r WHERE r.user_id = u.id AND r.role = 'LEARNER')
"""

COURSES_SQL = """
SELECT c.id::text AS course_id, c.institution_id::text AS institution_id,
       c.classification_code, c.education_type, c.tuition_type, c.tuition_amount, c.material_fee_type,
       c.target_audiences, c.credit_bank_eligible, c.capacity, c.waitlist_capacity,
       c.recruit_start_date, c.recruit_end_date, c.status, c.created_at,
       COALESCE(s.session_count, 0) AS session_count, s.first_session_at, COALESCE(a.active_count, 0) AS active_count
  FROM tb_courses c
  JOIN tb_institutions i ON i.id = c.institution_id
  LEFT JOIN (SELECT course_id, count(*) AS session_count, min(starts_at) AS first_session_at
               FROM tb_course_sessions WHERE status <> 'CANCELLED' GROUP BY course_id) s ON s.course_id = c.id
  LEFT JOIN (SELECT course_id, count(*) AS active_count
               FROM tb_enrollments WHERE status IN ('APPROVED', 'WAITLISTED') GROUP BY course_id) a ON a.course_id = c.id
 WHERE c.deleted_at IS NULL
   AND c.status NOT IN ('DRAFT', 'CANCELLED')
"""

# 양성(§12.3.1)과 인기 재계산(§3.3) 모두 이 표에서 나온다. 연령대는 PopularityMapper.ageBand 와 같은 식이다.
ENROLLMENTS_SQL = """
SELECT e.user_id::text AS user_id, e.course_id::text AS course_id, e.applied_at,
       (e.status IN ('APPLIED', 'WAITLISTED', 'APPROVED') OR cp.status = 'COMPLETED') AS positive,
       CASE WHEN cp.status = 'COMPLETED' THEN COALESCE(cp.completed_at, cp.updated_at) END AS completed_at,
       CASE WHEN u.birth_date IS NULL THEN NULL
            ELSE LEAST(GREATEST((EXTRACT(YEAR FROM age((e.applied_at AT TIME ZONE 'Asia/Seoul')::date,
                                                       u.birth_date))::int / 10) * 10, 10), 70)::text
       END AS age_band
  FROM tb_enrollments e
  JOIN tb_users u ON u.id = e.user_id
  JOIN tb_courses c ON c.id = e.course_id
  LEFT JOIN tb_completions cp ON cp.enrollment_id = e.id
 WHERE c.deleted_at IS NULL
"""

INTERESTS_SQL = "SELECT user_id::text AS user_id, course_id::text AS course_id, created_at FROM tb_course_interests"
USER_INTERESTS_SQL = "SELECT user_id::text AS user_id, classification_code, created_at FROM tb_user_interests"
IMPRESSIONS_SQL = (
    "SELECT user_id::text AS user_id, course_id::text AS course_id, shown_at FROM tb_recommendation_impressions"
)

_TIMESTAMP_COLUMNS = ("created_at", "applied_at", "completed_at", "shown_at", "first_session_at")
_DATE_COLUMNS = ("birth_date", "recruit_start_date", "recruit_end_date")
_NUMERIC_COLUMNS = ("tuition_amount", "capacity", "waitlist_capacity", "session_count", "active_count")


def _frame(conn, sql: str) -> pd.DataFrame:
    with conn.cursor() as cur:
        cur.execute(sql)
        columns = [d.name for d in cur.description]
        frame = pd.DataFrame(cur.fetchall(), columns=columns)
    for column in frame.columns:
        if column in _TIMESTAMP_COLUMNS:
            frame[column] = pd.to_datetime(frame[column], utc=True)
        elif column in _DATE_COLUMNS:
            frame[column] = pd.to_datetime(frame[column])
        elif column in _NUMERIC_COLUMNS:
            frame[column] = pd.to_numeric(frame[column].map(lambda v: float(v) if isinstance(v, Decimal) else v))
    if "positive" in frame.columns:
        frame["positive"] = frame["positive"].fillna(False).astype(bool)
    return frame


def read_all(conn) -> RawData:
    """한 읽기 트랜잭션(같은 스냅샷)에서 여섯 표를 읽는다."""
    with conn.transaction():
        with conn.cursor() as cur:
            cur.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY")
        return RawData(
            users=_frame(conn, USERS_SQL),
            courses=_frame(conn, COURSES_SQL),
            enrollments=_frame(conn, ENROLLMENTS_SQL),
            interests=_frame(conn, INTERESTS_SQL),
            impressions=_frame(conn, IMPRESSIONS_SQL),
            user_interests=_frame(conn, USER_INTERESTS_SQL),
        )


STALE_RUN_MESSAGE = "이전 실행이 끝나지 않음 — 스케줄러 타임아웃(강제 종료) 또는 서버 중단으로 추정"


def fail_stale_runs(conn) -> int:
    """시작 시 남아 있는 RUNNING 행을 FAILED 로 닫는다. 배치는 advisory lock 으로 한 번에 하나만 돌므로
    시작 시점의 RUNNING 행은 모두 끝나지 못한 이전 실행이다(SIGKILL 은 fail_run 을 거치지 못한다). 닫은 행 수를 돌려준다."""
    with conn.transaction(), conn.cursor() as cur:
        cur.execute(
            "UPDATE tb_recommendation_model_runs SET result = 'FAILED', finished_at = %s, message = %s"
            " WHERE result = 'RUNNING'",
            (datetime.now().astimezone(), STALE_RUN_MESSAGE),
        )
        return cur.rowcount if cur.rowcount is not None and cur.rowcount > 0 else 0


def insert_run(conn, run_id, started_at: datetime) -> None:
    with conn.transaction(), conn.cursor() as cur:
        cur.execute("INSERT INTO tb_recommendation_model_runs (id, started_at, result) VALUES (%s, %s, 'RUNNING')",
                    (run_id, started_at))


@dataclass(frozen=True)
class RunRecord:
    result: str
    finished_at: datetime
    model_type: str | None = None
    positive_samples: int | None = None
    negative_samples: int | None = None
    user_count: int | None = None
    course_count: int | None = None
    auc: float | None = None
    precision_at_6: float | None = None
    rules_precision_at_6: float | None = None
    feature_importance: list | None = None
    report_path: str | None = None
    message: str | None = None


def _rounded(value: float | None) -> float | None:
    return None if value is None else round(float(value), 4)


def _update_run(cur, run_id, record: RunRecord) -> None:
    cur.execute(
        """
        UPDATE tb_recommendation_model_runs
           SET finished_at = %s, result = %s, model_type = %s, positive_samples = %s, negative_samples = %s,
               user_count = %s, course_count = %s, auc = %s, precision_at_6 = %s, rules_precision_at_6 = %s,
               feature_importance = %s::jsonb, report_path = %s, message = %s
         WHERE id = %s
        """,
        (record.finished_at, record.result, record.model_type, record.positive_samples, record.negative_samples,
         record.user_count, record.course_count, _rounded(record.auc), _rounded(record.precision_at_6),
         _rounded(record.rules_precision_at_6),
         None if record.feature_importance is None else json.dumps(record.feature_importance, ensure_ascii=False),
         None if record.report_path is None else record.report_path[:REPORT_PATH_LIMIT],
         None if record.message is None else record.message[:MESSAGE_LIMIT], run_id),
    )


def finish_without_scores(conn, run_id, record: RunRecord) -> None:
    """학습을 건너뛰거나 규칙보다 나쁠 때: 점수 테이블을 비우고 run 행을 끝낸다(한 트랜잭션)."""
    with conn.transaction(), conn.cursor() as cur:
        cur.execute("DELETE FROM tb_course_recommendation_scores")
        _update_run(cur, run_id, record)


def finish_with_scores(conn, run_id, record: RunRecord, scores: pd.DataFrame, computed_at: datetime) -> int:
    """한 트랜잭션에서 점수 전체를 지우고 새 행을 넣은 뒤 run 행을 TRAINED 로 바꾼다. 넣은 행 수를 돌려준다."""
    run_text = str(run_id)
    with conn.transaction(), conn.cursor() as cur:
        cur.execute("DELETE FROM tb_course_recommendation_scores")
        copy_sql = ("COPY tb_course_recommendation_scores (user_id, course_id, score, top_factor, run_id, computed_at)"
                    " FROM STDIN")
        with cur.copy(copy_sql) as copy:
            for user_id, course_id, score, factor in scores[["user_id", "course_id", "score", "top_factor"]].itertuples(
                    index=False, name=None):
                copy.write_row((user_id, course_id, f"{min(max(float(score), 0.0), 1.0):.5f}", factor,
                                run_text, computed_at))
        _update_run(cur, run_id, record)
    return len(scores)


def fail_run(conn, run_id, message: str) -> None:
    """실패 기록. 점수 테이블은 건드리지 않는다 — 이전 run 의 점수가 그대로 쓰인다(API 영향 없음)."""
    with conn.transaction(), conn.cursor() as cur:
        _update_run(cur, run_id, RunRecord(result="FAILED", finished_at=datetime.now().astimezone(), message=message))
