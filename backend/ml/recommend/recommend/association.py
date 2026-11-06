"""§12.3.3 연관도 분석과 feature 선정, Markdown 리포트.

- 범주 × 범주: χ² 검정 + Cramer's V. 기대빈도 5 미만 칸이 20% 를 넘으면 "표본 부족".
- 범주 × 연속: 2군 t-test(Welch), 3군 이상 ANOVA. 어느 집단이든 30 미만이면 "표본 부족".
- 연속 × 연속: Pearson·Spearman. 30 미만이면 "표본 부족".
표본 부족인 쌍은 결론을 쓰지 않는다. 그 feature 는 판단을 보류하고 유지한다.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from scipy import stats

from .features import ALWAYS_KEEP, UNKNOWN

INSUFFICIENT_LABEL = "표본 부족"
MIN_EXPECTED = 5
SPARSE_SHARE = 0.2
MIN_GROUP_N = 30
MIN_STRENGTH = 0.1
MAX_P = 0.05


@dataclass(frozen=True)
class ChiSquareResult:
    pair: str
    n: int
    chi2: float
    dof: int
    p_value: float
    cramers_v: float
    insufficient: bool


@dataclass(frozen=True)
class GroupTestResult:
    pair: str
    method: str
    groups: int
    n: int
    statistic: float
    p_value: float
    insufficient: bool


@dataclass(frozen=True)
class CorrelationResult:
    pair: str
    n: int
    pearson: float
    pearson_p: float
    spearman: float
    spearman_p: float
    insufficient: bool


@dataclass(frozen=True)
class AssociationReport:
    categorical: tuple = ()
    group_tests: tuple = ()
    correlations: tuple = ()
    feature_tests: dict = field(default_factory=dict)


def cramers_v(chi2: float, n: int, shape: tuple[int, int]) -> float:
    k = min(shape) - 1
    if n <= 0 or k <= 0:
        return 0.0
    return math.sqrt(chi2 / (n * k))


def is_sparse_table(expected: np.ndarray) -> bool:
    expected = np.asarray(expected, dtype=float)
    return expected.size == 0 or float((expected < MIN_EXPECTED).mean()) > SPARSE_SHARE


def _clean_pair(a: pd.Series, b: pd.Series) -> tuple[pd.Series, pd.Series]:
    frame = pd.DataFrame({"a": a.reset_index(drop=True), "b": b.reset_index(drop=True)}).dropna()
    frame = frame[(frame["a"].astype(str) != UNKNOWN) & (frame["b"].astype(str) != UNKNOWN)]
    return frame["a"], frame["b"]


def chi_square(a: pd.Series, b: pd.Series, pair: str) -> ChiSquareResult:
    a, b = _clean_pair(a, b)
    table = pd.crosstab(a.astype(str), b.astype(str))
    n = int(table.to_numpy().sum())
    if min(table.shape, default=0) < 2:
        return ChiSquareResult(pair, n, math.nan, 0, math.nan, math.nan, True)
    chi2, p_value, dof, expected = stats.chi2_contingency(table.to_numpy(), correction=False)
    return ChiSquareResult(pair, n, float(chi2), int(dof), float(p_value),
                           cramers_v(float(chi2), n, table.shape), is_sparse_table(expected))


def group_mean_test(groups: pd.Series, values: pd.Series, pair: str) -> GroupTestResult:
    groups, values = _clean_pair(groups, values)
    samples = [values[groups == g].astype(float).to_numpy() for g in sorted(groups.unique(), key=str)]
    method = "t-test" if len(samples) == 2 else "ANOVA"
    too_small = len(samples) < 2 or min(len(s) for s in samples) < MIN_GROUP_N
    if too_small or all(np.ptp(s) == 0 for s in samples):
        return GroupTestResult(pair, method, len(samples), len(values), math.nan, math.nan, True)
    if method == "t-test":
        statistic, p_value = stats.ttest_ind(samples[0], samples[1], equal_var=False)
    else:
        statistic, p_value = stats.f_oneway(*samples)
    return GroupTestResult(pair, method, len(samples), len(values), float(statistic), float(p_value), False)


def correlation(x: pd.Series, y: pd.Series, pair: str) -> CorrelationResult:
    x, y = _clean_pair(x, y)
    x, y = x.astype(float), y.astype(float)
    if len(x) < MIN_GROUP_N or x.nunique() < 2 or y.nunique() < 2:
        return CorrelationResult(pair, len(x), math.nan, math.nan, math.nan, math.nan, True)
    pearson = stats.pearsonr(x, y)
    spearman = stats.spearmanr(x, y)
    return CorrelationResult(pair, len(x), float(pearson[0]), float(pearson[1]),
                             float(spearman[0]), float(spearman[1]), False)


def is_weak(result) -> bool:
    """선정 규칙: Cramer's V 0.1 미만·상관 0.1 미만·p 0.05 초과면 약하다. 표본 부족이면 판단하지 않는다(False)."""
    if result.insufficient:
        return False
    if isinstance(result, ChiSquareResult):
        return result.cramers_v < MIN_STRENGTH or result.p_value > MAX_P
    if isinstance(result, CorrelationResult):
        strength = max(abs(result.pearson), abs(result.spearman))
        return strength < MIN_STRENGTH or min(result.pearson_p, result.spearman_p) > MAX_P
    return result.p_value > MAX_P


def verdict(result) -> str:
    if result.insufficient:
        return INSUFFICIENT_LABEL
    if is_weak(result):
        return "관계 약함 또는 유의하지 않음"
    return "관계 있음"


def analyze(table: pd.DataFrame) -> AssociationReport:
    """학습 표(rows + feature + label + age_years)로 §12.3.3 의 쌍을 계산한다. 범주 쌍은 신청한 행으로 본다."""
    positives = table[table["label"] == 1]
    activity = table["interest_count"] + table["enrollment_count"] + table["completion_count"]
    categorical = {
        "age_band": chi_square(positives["age_band"], positives["class_l1"], "연령대×대분류"),
        "gender": chi_square(positives["gender"], positives["class_l1"], "성별×대분류"),
        "education_type": chi_square(positives["age_band"], positives["education_type"], "연령대×교육형태"),
    }
    group_tests = {
        "tuition_amount": group_mean_test(table["label"], table["tuition_amount"], "신청 여부×수강료"),
        "session_count": group_mean_test(table["label"], table["session_count"], "신청 여부×회차 수"),
    }
    age_by_major = group_mean_test(positives["class_l1"], positives["age_years"], "대분류×나이")
    correlations = (
        correlation(positives["age_years"], positives["tuition_amount"], "나이×수강료"),
        correlation(activity, table["pop_all"], "행동 수×인기 점수"),
    )
    return AssociationReport(tuple(categorical.values()), (*group_tests.values(), age_by_major), correlations,
                             {**categorical, **group_tests})


def select_features(candidates, tests: dict) -> tuple[list[str], dict]:
    """feature 선정. 분류 코드·matchLevel 은 통계와 무관하게 유지(§12.3.3)."""
    kept, dropped = [], {}
    for feature in candidates:
        result = tests.get(feature)
        if feature not in ALWAYS_KEEP and result is not None and is_weak(result):
            dropped[feature] = f"{result.pair}: {verdict(result)}"
        else:
            kept.append(feature)
    return kept, dropped


def _fmt(value, digits: int = 4) -> str:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return "-"
    if isinstance(value, float):
        return f"{value:.{digits}f}" if abs(value) >= 1e-4 or value == 0 else f"{value:.2e}"
    return str(value)


def _association_lines(report: AssociationReport) -> list[str]:
    lines = ["## 1. 범주 × 범주 (χ² + Cramer's V, 신청한 행)", "",
             "| 쌍 | n | χ² | 자유도 | p | Cramer's V | 판정 |", "|---|---|---|---|---|---|---|"]
    lines += [f"| {r.pair} | {r.n} | {_fmt(r.chi2, 2)} | {r.dof} | {_fmt(r.p_value)} | {_fmt(r.cramers_v)} | {verdict(r)} |"
              for r in report.categorical]
    lines += ["", "## 2. 범주 × 연속 (t-test / ANOVA)", "",
              "| 쌍 | 방법 | 집단 수 | n | 통계량 | p | 판정 |", "|---|---|---|---|---|---|---|"]
    lines += [f"| {r.pair} | {r.method} | {r.groups} | {r.n} | {_fmt(r.statistic, 3)} | {_fmt(r.p_value)} | {verdict(r)} |"
              for r in report.group_tests]
    lines += ["", "## 3. 연속 × 연속 (Pearson / Spearman)", "",
              "| 쌍 | n | Pearson r | p | Spearman ρ | p | 판정 |", "|---|---|---|---|---|---|---|"]
    lines += [f"| {r.pair} | {r.n} | {_fmt(r.pearson)} | {_fmt(r.pearson_p)} | {_fmt(r.spearman)} | "
              f"{_fmt(r.spearman_p)} | {verdict(r)} |" for r in report.correlations]
    return lines


def _training_lines(training: dict) -> list[str]:
    lines = ["", "## 5. 학습·평가", ""]
    for evaluation in training.get("evaluations", []):
        lines.append(f"- {evaluation['model_type']}: AUC {_fmt(evaluation['auc'])}, "
                     f"precision@6 {_fmt(evaluation['precision_at_6'])}")
    lines.append(f"- 1차 규칙 기준선 precision@6: {_fmt(training.get('rules_precision_at_6'))}")
    lines.append(f"- 운영 점수 모델: {training.get('model_type') or '-'}")
    importance = training.get("feature_importance") or []
    if importance:
        lines += ["", "## 6. feature importance 상위 10", "", "| 순위 | feature | 묶음 | 중요도 |", "|---|---|---|---|"]
        lines += [f"| {i} | {item['feature']} | {item['group'] or '-'} | {_fmt(item['importance'])} |"
                  for i, item in enumerate(importance, start=1)]
    return lines


def render_report(header: dict, report: AssociationReport | None, kept: list, dropped: dict, training: dict) -> str:
    """리포트 Markdown. header 는 run_id·생성 시각·표본 수·결과 코드·메시지."""
    lines = [f"# 강좌 추천 모델 리포트 — {header['date']}", "",
             f"- run: `{header['run_id']}`", f"- 결과: **{header['result']}**",
             f"- 표본: 양성 {header['positive']}건, 음성 {header['negative']}건, 활성 회원 {header['users']}명, "
             f"강좌 {header['courses']}개"]
    if header.get("message"):
        lines.append(f"- 비고: {header['message']}")
    lines.append("")
    if report is None:
        lines.append("학습 표가 비어 있어 연관도를 계산하지 않았다.")
    else:
        lines += _association_lines(report)
    lines += ["", "## 4. feature 선정", "",
              "규칙: Cramer's V 0.1 미만·상관 0.1 미만·p 0.05 초과면 뺀다. 분류 코드 3단계·matchLevel 은 유지한다. "
              "표본 부족인 쌍의 feature 는 판단을 보류하고 유지한다.", ""]
    lines.append(f"- 유지({len(kept)}): " + (", ".join(kept) if kept else "-"))
    lines.append("- 제외: " + ("; ".join(f"{k} ({v})" for k, v in dropped.items()) if dropped else "없음"))
    lines += _training_lines(training)
    lines += ["", "## 7. topFactor 산출", "",
              "XGBoost 는 행별 기여도(pred_contribs, SHAP 값)를, RandomForest 는 feature importance × 표준화 값을 "
              "feature 묶음(CLASSIFICATION·INTEREST·AGE_BAND·PRICE·SCHEDULE·POPULARITY)별로 더해 가장 큰 묶음을 쓴다. "
              "묶음에 속하지 않는 feature(성별·행동 건수·기관 등)는 점수에만 쓰인다.", ""]
    return "\n".join(lines)
