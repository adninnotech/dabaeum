"""학습 표에 이름·이메일·전화를 읽지 않는다(§12.7). 환경변수 값은 오류 메시지에 남기지 않는다."""

import re

import pytest

from recommend import db


def test_회원_조회는_이름_이메일_전화를_읽지_않는다():
    sql = db.USERS_SQL.lower()
    for column in ("name", "email", "phone"):
        assert not re.search(rf"\b{column}\b", sql)


def test_조회_SQL_은_삭제될_지역_학번_컬럼을_읽지_않는다():
    for sql in (db.USERS_SQL, db.ENROLLMENTS_SQL, db.COURSES_SQL, db.USER_INTERESTS_SQL):
        assert not re.search(r"region_code|learner_no|applicant_region", sql)
    assert "i.region" not in db.COURSES_SQL


def test_관심_분야_조회는_분류_코드만_읽는다():
    sql = db.USER_INTERESTS_SQL
    assert "tb_user_interests" in sql and "classification_code" in sql


def test_환경변수가_없으면_이름만_알리고_값은_쓰지_않는다():
    env = {"DABAEUM_DB_HOST": "h", "DABAEUM_DB_PORT": "5432", "DABAEUM_DB_PASSWORD": "s3cret"}
    with pytest.raises(db.MissingDatabaseSettings) as error:
        db.settings_from_env(env)
    message = str(error.value)
    assert "DABAEUM_DB_NAME" in message and "DABAEUM_DB_USERNAME" in message
    assert "s3cret" not in message


def test_포트가_숫자가_아니면_거부한다():
    env = {name: "x" for name in db.ENV_NAMES}
    with pytest.raises(db.MissingDatabaseSettings):
        db.settings_from_env(env)


def test_메시지에서_비밀값을_가린다():
    settings = db.settings_from_env(
        {"DABAEUM_DB_HOST": "h", "DABAEUM_DB_PORT": "5432", "DABAEUM_DB_NAME": "n",
         "DABAEUM_DB_USERNAME": "u", "DABAEUM_DB_PASSWORD": "s3cret"}
    )
    assert "s3cret" not in db.redact("password s3cret failed", settings)


class _FakeCursor:
    def __init__(self, store):
        self.store = store
        self.rowcount = 2

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def execute(self, sql, params=None):
        self.store.append((" ".join(sql.split()), params))


class _FakeConn:
    def __init__(self):
        self.executed = []
        self.transactions = 0

    def transaction(self):
        conn = self

        class _Tx:
            def __enter__(self_inner):
                conn.transactions += 1
                return self_inner

            def __exit__(self_inner, *exc):
                return False

        return _Tx()

    def cursor(self):
        return _FakeCursor(self.executed)


def test_시작_시_남은_RUNNING_행을_FAILED_로_닫고_점수는_건드리지_않는다():
    conn = _FakeConn()

    closed = db.fail_stale_runs(conn)

    assert closed == 2
    assert conn.transactions == 1
    (sql, params), = conn.executed
    assert sql.startswith("UPDATE tb_recommendation_model_runs SET result = 'FAILED'")
    assert "WHERE result = 'RUNNING'" in sql
    assert "tb_course_recommendation_scores" not in sql
    assert params[1] == db.STALE_RUN_MESSAGE

