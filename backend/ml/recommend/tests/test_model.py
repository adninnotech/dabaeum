"""회원당 precision@6 과 1차 규칙 기준선 정렬(§3.3), 모델 고르기."""

import math

import pandas as pd

from recommend.model import MODEL_RANDOM_FOREST, MODEL_XGBOOST, ModelEvaluation, choose_model, precision_at_k
from recommend.rules_baseline import rule_rank_scores, rules_precision_at_k


def test_precision_at_k_는_회원별_상위_k_건의_신청_비율_평균이다():
    frame = pd.DataFrame(
        {
            "user_id": ["u1"] * 8 + ["u2"] * 3 + ["u3"] * 2,
            "label": [1, 0, 1, 0, 0, 0, 0, 1] + [1, 0, 0] + [0, 0],
            "score": [0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2] + [0.1, 0.5, 0.3] + [0.9, 0.8],
        }
    )
    # u1: 상위 6건 중 2건 → 2/6. u2: 후보 3건뿐 → 1/3. u3: 양성 없음 → 제외.
    assert math.isclose(precision_at_k(frame, k=6), (2 / 6 + 1 / 3) / 2)


def test_양성이_없으면_precision_은_None():
    frame = pd.DataFrame({"user_id": ["u1"], "label": [0], "score": [0.3]})
    assert precision_at_k(frame, k=6) is None


def test_규칙_기준선은_일치수준_다음_인기_순이다():
    frame = pd.DataFrame(
        {
            "user_id": ["u1"] * 3,
            "course_id": ["a", "b", "c"],
            "match_level": [1, 3, 3],
            "pop_all": [100.0, 1.0, 5.0],
            "pop_age_band": [0.0, 0.0, 0.0],
            "interest_match": [0, 0, 0],
            "fresh_bonus": [0.0, 0.0, 0.0],
            "label": [0, 0, 1],
        }
    )
    ranked = frame.assign(score=rule_rank_scores(frame)).sort_values("score", ascending=False)
    assert ranked["course_id"].tolist() == ["c", "b", "a"]
    assert math.isclose(rules_precision_at_k(frame, k=1), 1.0)


def test_신호가_없으면_연령대_인기를_먼저_본다():
    frame = pd.DataFrame(
        {
            "user_id": ["u1"] * 2,
            "course_id": ["a", "b"],
            "match_level": [0, 0],
            "pop_all": [100.0, 1.0],
            "pop_age_band": [0.0, 9.0],
            "interest_match": [0, 0],
            "fresh_bonus": [0.0, 0.0],
            "label": [0, 1],
        }
    )
    ranked = frame.assign(score=rule_rank_scores(frame)).sort_values("score", ascending=False)
    assert ranked["course_id"].tolist() == ["b", "a"]


def test_두_모델이_비슷하면_XGBoost():
    xgb = ModelEvaluation(MODEL_XGBOOST, auc=0.80, precision_at_6=0.300)
    rf_close = ModelEvaluation(MODEL_RANDOM_FOREST, auc=0.81, precision_at_6=0.305)
    rf_better = ModelEvaluation(MODEL_RANDOM_FOREST, auc=0.85, precision_at_6=0.35)
    assert choose_model(xgb, rf_close).model_type == MODEL_XGBOOST
    assert choose_model(xgb, rf_better).model_type == MODEL_RANDOM_FOREST


def test_일치가_없으면_관심_분야_일치가_연령대_인기보다_먼저다():
    frame = pd.DataFrame(
        {
            "user_id": ["u1"] * 3,
            "course_id": ["a", "b", "c"],
            "match_level": [0, 0, 0],
            "pop_all": [100.0, 1.0, 5.0],
            "pop_age_band": [9.0, 0.0, 0.0],
            "interest_match": [0, 1, 0],
            "fresh_bonus": [0.0, 0.0, 0.0],
            "label": [0, 1, 0],
        }
    )
    ranked = frame.assign(score=rule_rank_scores(frame)).sort_values("score", ascending=False)
    assert ranked["course_id"].tolist() == ["b", "a", "c"]


def test_개인_일치가_관심_분야보다_먼저다():
    frame = pd.DataFrame(
        {
            "user_id": ["u1"] * 2,
            "course_id": ["a", "b"],
            "match_level": [1, 0],
            "pop_all": [1.0, 100.0],
            "pop_age_band": [0.0, 0.0],
            "interest_match": [0, 1],
            "fresh_bonus": [0.0, 0.0],
            "label": [1, 0],
        }
    )
    ranked = frame.assign(score=rule_rank_scores(frame)).sort_values("score", ascending=False)
    assert ranked["course_id"].tolist() == ["a", "b"]
