"""§12.3.3 연관도: 기대빈도 기준 표본 부족, Cramer's V 값, 집단 검정, 상관."""

import math

import numpy as np
import pandas as pd

from recommend.association import (
    INSUFFICIENT_LABEL,
    chi_square,
    correlation,
    cramers_v,
    group_mean_test,
    is_sparse_table,
    select_features,
    verdict,
)


def _expand(table):
    """교차표 → (a, b) 두 열."""
    a, b = [], []
    for i, row in enumerate(table):
        for j, count in enumerate(row):
            a += [f"r{i}"] * count
            b += [f"c{j}"] * count
    return pd.Series(a), pd.Series(b)


def test_cramers_v_는_교과서_공식과_같다():
    a, b = _expand([[60, 40], [30, 70]])
    result = chi_square(a, b, "a×b")
    n = 200
    expected = np.array([[45, 55], [45, 55]])
    observed = np.array([[60, 40], [30, 70]])
    chi2 = ((observed - expected) ** 2 / expected).sum()
    assert math.isclose(result.chi2, chi2, rel_tol=1e-9)
    assert math.isclose(result.cramers_v, math.sqrt(chi2 / n), rel_tol=1e-9)
    assert not result.insufficient
    assert result.p_value < 0.05


def test_cramers_v_는_작은_쪽_차원으로_나눈다():
    assert math.isclose(cramers_v(chi2=50.0, n=100, shape=(3, 5)), math.sqrt(50 / (100 * 2)))


def test_기대빈도_5_미만_칸이_20퍼센트를_넘으면_표본_부족():
    assert not is_sparse_table(np.array([[10, 10, 10, 10, 4.9]]))  # 1/5 = 20% 는 넘지 않는다
    assert is_sparse_table(np.array([[10, 10, 10, 4.9, 4.9]]))  # 2/5 = 40%


def test_작은_표본의_카이제곱은_표본_부족으로_표기하고_결론을_쓰지_않는다():
    a, b = _expand([[3, 1], [2, 4]])
    result = chi_square(a, b, "연령대×대분류")
    assert result.insufficient
    assert verdict(result) == INSUFFICIENT_LABEL


def test_범주가_하나뿐이면_검정할_수_없어_표본_부족():
    result = chi_square(pd.Series(["a"] * 50), pd.Series(["x", "y"] * 25), "a×b")
    assert result.insufficient


def test_두_집단이면_t_test_세_집단_이상이면_ANOVA():
    rng = np.random.default_rng(0)
    two = group_mean_test(pd.Series([0] * 50 + [1] * 50), pd.Series(np.r_[rng.normal(0, 1, 50), rng.normal(2, 1, 50)]), "신청×수강료")
    three = group_mean_test(pd.Series(["a", "b", "c"] * 40), pd.Series(rng.normal(0, 1, 120)), "대분류×나이")
    assert two.method == "t-test"
    assert two.p_value < 0.05
    assert three.method == "ANOVA"


def test_집단_표본이_30_미만이면_표본_부족():
    result = group_mean_test(pd.Series([0] * 29 + [1] * 50), pd.Series(np.arange(79, dtype=float)), "신청×회차 수")
    assert result.insufficient


def test_상관은_피어슨과_스피어만을_함께_낸다():
    x = pd.Series(np.arange(100, dtype=float))
    result = correlation(x, x ** 3, "나이×수강료")
    assert math.isclose(result.spearman, 1.0)
    assert 0.8 < result.pearson < 1.0
    assert not result.insufficient


def test_선정_규칙은_약한_변수를_빼되_분류와_일치수준은_유지한다():
    weak_a, weak_b = _expand([[50, 50], [49, 51]])
    strong_a, strong_b = _expand([[90, 10], [10, 90]])
    weak = chi_square(weak_a, weak_b, "성별×대분류")
    strong = chi_square(strong_a, strong_b, "연령대×대분류")
    tests = {"gender": weak, "age_band": strong, "class_l1": weak, "match_level": weak}
    kept, dropped = select_features(["gender", "age_band", "class_l1", "match_level", "pop_all"], tests)
    assert "gender" in dropped
    assert {"age_band", "class_l1", "match_level", "pop_all"} <= set(kept)
