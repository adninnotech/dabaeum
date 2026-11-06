"""1차 규칙(§3.3·§4)을 같은 검증 집합에 적용해 precision@6 을 낸다. 모델이 이보다 나쁘면 점수를 쓰지 않는다.

정렬: matchLevel 내림차순 → (일치가 있으면) 인기 점수 + 신규 보정 → (일치가 없으면) 관심 분야 일치 → 연령대 인기
→ 전체 인기 → course_id. Java 의 PERSONAL → INTEREST → AGE_BAND → POPULAR 순서를 한 줄 정렬로 옮긴 것이다.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from .model import PRECISION_K, precision_at_k

FRESH_DAYS = 7     # dabaeum.recommendation.fresh-days 기본값
FRESH_BONUS = 5.0  # dabaeum.recommendation.fresh-bonus 기본값


def fresh_bonus_at(rows: pd.DataFrame, course_created_at: pd.Series,
                   fresh_days: int = FRESH_DAYS, bonus: float = FRESH_BONUS) -> np.ndarray:
    """기준 시각 기준 fresh_days 일 안에 만든 강좌면 bonus(§3.3 신규 보정)."""
    created = pd.to_datetime(rows["course_id"].map(course_created_at), utc=True)
    at = pd.to_datetime(rows["event_at"], utc=True)
    fresh = (created <= at) & (created >= at - pd.Timedelta(days=fresh_days))
    return np.where(fresh.to_numpy(), bonus, 0.0)


def rule_rank_scores(frame: pd.DataFrame) -> pd.Series:
    """회원 안에서 규칙 순서가 앞일수록 큰 값."""
    matched = frame["match_level"] > 0
    keys = pd.DataFrame(
        {
            "user_id": frame["user_id"],
            "match_level": frame["match_level"],
            "personal": np.where(matched, frame["pop_all"] + frame["fresh_bonus"], 0.0),
            "interest": np.where(matched, 0.0, frame["interest_match"]),
            "age": np.where(matched, 0.0, frame["pop_age_band"]),
            "all": frame["pop_all"],
            "course_id": frame["course_id"].astype(str),
        },
        index=frame.index,
    )
    ordered = keys.sort_values(
        ["user_id", "match_level", "personal", "interest", "age", "all", "course_id"],
        ascending=[True, False, False, False, False, False, True], kind="stable",
    )
    position = ordered.groupby("user_id").cumcount()
    return (-position).reindex(frame.index).astype(float)


def rules_precision_at_k(frame: pd.DataFrame, k: int = PRECISION_K) -> float | None:
    return precision_at_k(frame.assign(score=rule_rank_scores(frame)), k=k)
