"""§12.4 최소 표본 판정과 결과 코드 → 종료 코드 매핑."""

from recommend.gate import (
    EXIT_ERROR,
    EXIT_REFUSED,
    EXIT_SKIPPED,
    EXIT_TRAINED,
    RunResult,
    SampleCounts,
    SampleThresholds,
    check_sample,
    decide_after_evaluation,
    exit_code_for,
)


def test_기준을_정확히_채우면_통과한다():
    decision = check_sample(SampleCounts(positives=2000, users=300, courses=50), SampleThresholds())
    assert decision.passed
    assert decision.shortfalls == ()


def test_양성_표본이_모자라면_건너뛰고_종료_코드는_3이다():
    decision = check_sample(SampleCounts(positives=1999, users=300, courses=50), SampleThresholds())
    assert not decision.passed
    assert decision.result is RunResult.SKIPPED_INSUFFICIENT_DATA
    assert exit_code_for(decision.result) == 3
    assert any("양성" in reason for reason in decision.shortfalls)


def test_회원과_강좌가_모자라면_사유를_모두_남긴다():
    decision = check_sample(SampleCounts(positives=5000, users=299, courses=49), SampleThresholds())
    assert not decision.passed
    assert len(decision.shortfalls) == 2


def test_최소_양성_기준은_인자로_바꿀_수_있다():
    decision = check_sample(SampleCounts(positives=100, users=300, courses=50), SampleThresholds(min_positive=100))
    assert decision.passed
    assert decision.result is None


def test_지금_dev_규모는_표본_부족이다():
    decision = check_sample(SampleCounts(positives=40, users=20, courses=30), SampleThresholds())
    assert decision.result is RunResult.SKIPPED_INSUFFICIENT_DATA


def test_규칙보다_나쁘면_점수를_쓰지_않는다():
    assert decide_after_evaluation(0.20, 0.25) is RunResult.SKIPPED_WORSE_THAN_RULES
    assert decide_after_evaluation(0.25, 0.25) is RunResult.TRAINED
    assert decide_after_evaluation(0.30, 0.25) is RunResult.TRAINED


def test_규칙_기준선을_못_구하면_모델_결과를_쓴다():
    assert decide_after_evaluation(0.30, None) is RunResult.TRAINED


def test_종료_코드_규약():
    assert exit_code_for(RunResult.TRAINED) == EXIT_TRAINED == 0
    assert exit_code_for(RunResult.SKIPPED_INSUFFICIENT_DATA) == EXIT_SKIPPED == 3
    assert exit_code_for(RunResult.SKIPPED_WORSE_THAN_RULES) == EXIT_SKIPPED
    assert exit_code_for(RunResult.FAILED) == EXIT_ERROR == 1
    assert EXIT_REFUSED == 2
