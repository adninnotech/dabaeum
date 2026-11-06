"""§12.3.1 타깃·음성 추출과 시점 누출 방지."""

import numpy as np
import pandas as pd

from recommend.dataset import (
    behaviour_before,
    build_events,
    build_positives,
    impression_negatives,
    random_negatives,
    time_split,
)

T0 = pd.Timestamp("2026-01-01T00:00:00Z")


def _days(n):
    return T0 + pd.Timedelta(days=n)


def _courses(count=30):
    return pd.DataFrame(
        {
            "course_id": [f"c{i}" for i in range(count)],
            "institution_id": ["i1"] * count,
            "classification_code": ["TSIX_040102" if i % 2 == 0 else "TSIX_050303" for i in range(count)],
            "tuition_type": ["PAID" if i % 3 == 0 else "FREE" for i in range(count)],
            "recruit_start_date": [pd.Timestamp("2025-12-01")] * count,
            "recruit_end_date": [pd.Timestamp("2026-12-31")] * count,
        }
    )


def test_신청_시각_이전_행동만_센다():
    courses = _courses()
    enrollments = pd.DataFrame(
        {
            "user_id": ["u1", "u1", "u1"],
            "course_id": ["c0", "c2", "c4"],
            "applied_at": [_days(1), _days(5), _days(10)],
            "positive": [True, True, True],
            "completed_at": [_days(3), pd.NaT, pd.NaT],
        }
    )
    interests = pd.DataFrame({"user_id": ["u1"], "course_id": ["c1"], "created_at": [_days(7)]})
    events = build_events(interests, enrollments, courses)
    rows = pd.DataFrame(
        {
            "user_id": ["u1", "u1", "u1"],
            # 첫 행은 c2 자신의 신청 시각(_days(5)) — 자기 신청은 세지 않는다.
            "event_at": [_days(5), _days(8), _days(0)],
            "classification_code": ["TSIX_040102", "TSIX_050303", "TSIX_040102"],
            "institution_id": ["i1", "i1", "i1"],
        }
    )
    behaviour = behaviour_before(rows, events)

    assert behaviour["enrollment_count"].tolist() == [1, 2, 0]
    assert behaviour["completion_count"].tolist() == [1, 1, 0]
    assert behaviour["interest_count"].tolist() == [0, 1, 0]
    # _days(5) 이전엔 c0(TSIX_040102) 만 있어 소분류 일치 3. _days(8) 이전엔 c1(TSIX_050303) 찜이 있어 3.
    assert behaviour["match_level"].tolist() == [3, 3, 0]
    assert behaviour["same_institution_before"].tolist() == [1, 1, 0]
    assert behaviour["days_since_last_action"].iloc[0] == 2.0


def test_미래_행동은_분류_일치에_들어가지_않는다():
    courses = _courses()
    enrollments = pd.DataFrame(
        {
            "user_id": ["u1"],
            "course_id": ["c1"],
            "applied_at": [_days(10)],
            "positive": [True],
            "completed_at": [pd.NaT],
        }
    )
    events = build_events(interests=pd.DataFrame(columns=["user_id", "course_id", "created_at"]),
                          enrollments=enrollments, courses=courses)
    rows = pd.DataFrame(
        {
            "user_id": ["u1"],
            "event_at": [_days(9)],
            "classification_code": ["TSIX_050303"],
            "institution_id": ["i1"],
        }
    )
    assert behaviour_before(rows, events)["match_level"].tolist() == [0]


def test_양성은_신청_또는_수료이고_회원쌍마다_가장_이른_시각_한_건이다():
    enrollments = pd.DataFrame(
        {
            "user_id": ["u1", "u1", "u2", "u3"],
            "course_id": ["c1", "c1", "c1", "c1"],
            "applied_at": [_days(3), _days(1), _days(2), _days(2)],
            "positive": [True, True, False, True],
            "completed_at": [pd.NaT] * 4,
        }
    )
    users = pd.DataFrame({"user_id": ["u1", "u2"]})
    positives = build_positives(enrollments, users)
    assert positives[["user_id", "course_id"]].values.tolist() == [["u1", "c1"]]
    assert positives["event_at"].iloc[0] == _days(1)
    assert positives["label"].iloc[0] == 1


def test_노출됐지만_신청하지_않은_쌍만_음성이다():
    impressions = pd.DataFrame(
        {"user_id": ["u1", "u1", "u1"], "course_id": ["c1", "c2", "c2"], "shown_at": [_days(1), _days(2), _days(3)]}
    )
    enrollments = pd.DataFrame(
        {"user_id": ["u1"], "course_id": ["c1"], "applied_at": [_days(2)], "positive": [True], "completed_at": [pd.NaT]}
    )
    users = pd.DataFrame({"user_id": ["u1"]})
    negatives = impression_negatives(impressions, enrollments, users)
    assert negatives["course_id"].tolist() == ["c2"]
    assert negatives["event_at"].tolist() == [_days(2)]
    assert (negatives["label"] == 0).all()


def test_무작위_음성은_양성의_4배이고_신청한_쌍과_겹치지_않는다():
    courses = _courses(40)
    positives = pd.DataFrame(
        {
            "user_id": [f"u{i % 5}" for i in range(20)],
            "course_id": [f"c{i}" for i in range(20)],
            "event_at": [_days(i) for i in range(20)],
            "label": 1,
        }
    )
    excluded = set(zip(positives["user_id"], positives["course_id"]))
    negatives = random_negatives(positives, courses, excluded, ratio=4, rng=np.random.default_rng(7))

    assert len(negatives) == 4 * len(positives)
    pairs = set(zip(negatives["user_id"], negatives["course_id"]))
    assert not pairs & excluded
    assert (negatives["label"] == 0).all()
    assert set(negatives["event_at"]) <= set(positives["event_at"])


def test_무작위_음성은_그_시각에_모집_중인_강좌에서만_뽑는다():
    courses = _courses(10).assign(
        recruit_start_date=[pd.Timestamp("2025-01-01")] * 5 + [pd.Timestamp("2026-06-01")] * 5,
        recruit_end_date=[pd.Timestamp("2026-12-31")] * 5 + [pd.Timestamp("2026-12-31")] * 5,
    )
    positives = pd.DataFrame({"user_id": ["u1"], "course_id": ["c0"], "event_at": [_days(1)], "label": 1})
    negatives = random_negatives(positives, courses, {("u1", "c0")}, ratio=4, rng=np.random.default_rng(1))
    assert set(negatives["course_id"]) <= {"c1", "c2", "c3", "c4"}


def test_시간순_분할은_마지막_20퍼센트_기간을_검증으로_둔다():
    rows = pd.DataFrame(
        {"event_at": [_days(i) for i in range(101)], "label": [i % 2 for i in range(101)]}
    )
    train, valid = time_split(rows, valid_fraction=0.2)
    assert rows.loc[train, "event_at"].max() < rows.loc[valid, "event_at"].min()
    assert rows.loc[valid, "event_at"].min() == _days(80)
    assert len(train) + len(valid) == len(rows)
