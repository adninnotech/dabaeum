"""§12.4 최소 표본 판정, 결과 코드, 종료 코드(§12.2), 가상 데이터 거부 대상 DB(§12.8)."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

EXIT_TRAINED = 0
EXIT_ERROR = 1
EXIT_REFUSED = 2
EXIT_SKIPPED = 3

# 가상 데이터를 넣으면 안 되는 DB. 이름이 이것과 같거나 알 수 없으면 거부한다.
FORBIDDEN_DATABASES = frozenset({"dabaeum_dev", "dabaeum_prd"})


class RunResult(str, Enum):
    """tb_recommendation_model_runs.result 값과 같다(V55 CHECK)."""

    RUNNING = "RUNNING"
    TRAINED = "TRAINED"
    SKIPPED_INSUFFICIENT_DATA = "SKIPPED_INSUFFICIENT_DATA"
    SKIPPED_WORSE_THAN_RULES = "SKIPPED_WORSE_THAN_RULES"
    FAILED = "FAILED"


_EXIT_CODES = {
    RunResult.TRAINED: EXIT_TRAINED,
    RunResult.SKIPPED_INSUFFICIENT_DATA: EXIT_SKIPPED,
    RunResult.SKIPPED_WORSE_THAN_RULES: EXIT_SKIPPED,
    RunResult.FAILED: EXIT_ERROR,
    RunResult.RUNNING: EXIT_ERROR,
}


@dataclass(frozen=True)
class SampleThresholds:
    """§8.1 "최소" 행. 양성 표본·활성 회원(양성 1건 이상)·강좌(양성 1건 이상)."""

    min_positive: int = 2000
    min_users: int = 300
    min_courses: int = 50


@dataclass(frozen=True)
class SampleCounts:
    positives: int
    users: int
    courses: int


@dataclass(frozen=True)
class GateDecision:
    passed: bool
    shortfalls: tuple[str, ...]

    @property
    def result(self) -> RunResult | None:
        """통과하면 None(학습 계속), 아니면 SKIPPED_INSUFFICIENT_DATA."""
        return None if self.passed else RunResult.SKIPPED_INSUFFICIENT_DATA


def check_sample(counts: SampleCounts, thresholds: SampleThresholds) -> GateDecision:
    checks = (
        ("양성 표본", "건", counts.positives, thresholds.min_positive),
        ("활성 회원", "명", counts.users, thresholds.min_users),
        ("강좌", "개", counts.courses, thresholds.min_courses),
    )
    shortfalls = tuple(
        f"{label} {actual}{unit} < 기준 {required}{unit}"
        for label, unit, actual, required in checks if actual < required
    )
    return GateDecision(passed=not shortfalls, shortfalls=shortfalls)


def decide_after_evaluation(model_precision: float | None, rules_precision: float | None) -> RunResult:
    """규칙 기준선보다 나쁘면 점수를 쓰지 않는다(§12.4). 같으면 모델을 쓴다."""
    if model_precision is None:
        return RunResult.SKIPPED_INSUFFICIENT_DATA
    if rules_precision is not None and model_precision < rules_precision:
        return RunResult.SKIPPED_WORSE_THAN_RULES
    return RunResult.TRAINED


def exit_code_for(result: RunResult) -> int:
    return _EXIT_CODES[result]


def is_forbidden_database(name: str | None) -> bool:
    if name is None or not name.strip():
        return True
    return name.strip().lower() in FORBIDDEN_DATABASES
