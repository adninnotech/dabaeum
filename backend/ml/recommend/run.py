#!/usr/bin/env python3
"""강좌 추천 모델 밤 배치 진입점 (docs/specs/course-recommendation.md §12.2~§12.5).

읽기 → 전처리 → 연관도 리포트 → feature 선정 → 표본 기준 → 학습·평가(규칙 대비) → 점수 기록.
종료 코드: 0 학습·점수 기록, 3 건너뜀(표본 부족·규칙보다 나쁨, 점수 테이블 비움), 1 실패.
stdout 마지막 줄은 결과 요약 JSON 한 줄이다(스케줄러가 이 줄만 로그로 남긴다). 진행 로그는 stderr.
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import traceback
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402

from recommend import association, dataset, db, features, gate, model, rules_baseline  # noqa: E402

LOG = logging.getLogger("recommend")
SCRIPT_DIR = Path(__file__).resolve().parent
SCORING_CHUNK_USERS = 500
VALID_FRACTION = 0.2


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="강좌 추천 모델 학습·점수 배치")
    parser.add_argument("--min-positive", type=int, default=2000, help="최소 양성 표본(§12.4)")
    parser.add_argument("--min-users", type=int, default=300, help="최소 활성 회원(양성 1건 이상)")
    parser.add_argument("--min-courses", type=int, default=50, help="최소 강좌(양성 1건 이상)")
    parser.add_argument("--top-n", type=int, default=50, help="회원당 저장할 점수 행 수")
    parser.add_argument("--report-dir", type=Path, default=SCRIPT_DIR.parent / "reports")
    parser.add_argument("--model-dir", type=Path, default=SCRIPT_DIR.parent / "models")
    parser.add_argument("--popularity-window-days", type=int, default=30, help="dabaeum.popularity.window-days")
    parser.add_argument("--negative-ratio", type=int, default=dataset.NEGATIVE_RATIO)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args(argv)
    if min(args.min_positive, args.min_users, args.min_courses, args.top_n, args.popularity_window_days) < 1:
        parser.error("수치 인자는 1 이상이어야 합니다")
    return args


@dataclass(frozen=True)
class Training:
    evaluations: tuple
    chosen: model.ModelEvaluation
    rules_precision: float | None
    categorical: tuple
    numeric: tuple


@dataclass(frozen=True)
class Outcome:
    result: gate.RunResult
    counts: gate.SampleCounts
    negatives: int
    report: association.AssociationReport | None
    kept: list
    dropped: dict
    message: str | None = None
    training: Training | None = None
    final_model: model.TrainedModel | None = None
    importance: list = field(default_factory=list)
    scores: pd.DataFrame | None = None


def _pop_events(raw: db.RawData) -> pd.DataFrame:
    return raw.enrollments[raw.enrollments["positive"]][["course_id", "applied_at", "age_band"]]


def feature_table(rows, raw, course_part, events, window_days) -> pd.DataFrame:
    """행(user_id, course_id, event_at, ...)마다 §12.3.2 feature 를 기준 시각 이전 정보로 만든다."""
    rows = rows.reset_index(drop=True)
    course_index = raw.courses.set_index("course_id")
    user_part = features.user_features_at(rows, raw.users)
    keyed = rows.assign(classification_code=rows["course_id"].map(course_index["classification_code"]),
                        institution_id=rows["course_id"].map(course_index["institution_id"]))
    behaviour = dataset.behaviour_before(keyed, events)
    segments = rows.assign(age_band=user_part["age_band"].to_numpy())
    popularity = dataset.popularity_at(segments, _pop_events(raw), window_days)
    table = features.assemble(rows, user_part, course_part, behaviour, popularity)
    table = pd.concat([table, features.interest_features(table, table["class_l1"], raw.user_interests)], axis=1)
    table["fresh_bonus"] = rules_baseline.fresh_bonus_at(rows, course_index["created_at"])
    return table


def _counts(rows: pd.DataFrame) -> gate.SampleCounts:
    positive = rows[rows["label"] == 1]
    return gate.SampleCounts(int(len(positive)), int(positive["user_id"].nunique()),
                             int(positive["course_id"].nunique()))


def train_and_evaluate(table: pd.DataFrame, kept: list, seed: int) -> Training | None:
    """시간순 분할로 두 모델과 규칙 기준선을 같은 검증 집합에서 잰다. 분할이 쓸 수 없으면 None."""
    train_idx, valid_idx = dataset.time_split(table, VALID_FRACTION)
    train, valid = table.loc[train_idx], table.loc[valid_idx]
    if train["label"].nunique() < 2 or valid["label"].nunique() < 2:
        return None
    categorical = tuple(f for f in features.CATEGORICAL_FEATURES if f in kept)
    numeric = tuple(f for f in features.NUMERIC_FEATURES if f in kept)
    spec = features.fit_spec(train, categorical, numeric)
    x_train, x_valid = features.encode(train, spec), features.encode(valid, spec)
    evaluations = tuple(
        model.evaluate(model.train(kind, x_train, train["label"], seed), x_valid, valid)
        for kind in (model.MODEL_XGBOOST, model.MODEL_RANDOM_FOREST)
    )
    for evaluation in evaluations:
        LOG.info("검증 %s: AUC %s, precision@6 %s", evaluation.model_type, evaluation.auc, evaluation.precision_at_6)
    chosen = model.choose_model(*evaluations)
    return Training(evaluations, chosen, rules_baseline.rules_precision_at_k(valid), categorical, numeric)


def score_all(trained, spec, raw, course_part, events, now, args) -> pd.DataFrame:
    """활성 학습자 × 모집 중 강좌에 점수를 매기고 회원당 상위 top_n 건과 topFactor 를 돌려준다."""
    today = pd.Timestamp(now).tz_convert(dataset.SEOUL).tz_localize(None).normalize()
    eligible = dataset.recruiting_courses(raw.courses, today)
    positive = raw.enrollments[raw.enrollments["positive"]]
    excluded = set(zip(positive["user_id"], positive["course_id"])) | set(
        zip(raw.interests["user_id"], raw.interests["course_id"]))
    parts = []
    for start in range(0, len(raw.users), SCORING_CHUNK_USERS):
        rows = dataset.scoring_rows(raw.users.iloc[start:start + SCORING_CHUNK_USERS], eligible, excluded,
                                    pd.Timestamp(now))
        if rows.empty:
            continue
        table = feature_table(rows.assign(label=0, source="SCORING"), raw, course_part, events,
                              args.popularity_window_days)
        encoded = features.encode(table, spec)
        scored = table[["user_id", "course_id"]].assign(score=model.predict(trained, encoded))
        top = scored.sort_values(["user_id", "score"], ascending=[True, False], kind="stable")
        top = top[top.groupby("user_id").cumcount() < args.top_n]
        parts.append(top.assign(top_factor=model.top_factors(trained, encoded.loc[top.index])))
    if not parts:
        return pd.DataFrame(columns=["user_id", "course_id", "score", "top_factor"])
    return pd.concat(parts, ignore_index=True)


def run_pipeline(raw: db.RawData, args, now: datetime) -> Outcome:
    """DB 를 쓰지 않는 본체. 파일·DB 기록은 main 이 한다."""
    rng = np.random.default_rng(args.seed)
    course_part = features.course_features(raw.courses, features.group_institutions(raw.courses))
    events = dataset.build_events(raw.interests, raw.enrollments, raw.courses)
    rows = dataset.build_training_rows(raw.enrollments, raw.interests, raw.impressions, raw.users, raw.courses,
                                       rng, ratio=args.negative_ratio)
    rows = rows[rows["course_id"].isin(raw.courses["course_id"])].reset_index(drop=True)
    counts, negatives = _counts(rows), int((rows["label"] == 0).sum())
    LOG.info("학습 표: 양성 %d, 음성 %d, 회원 %d, 강좌 %d", counts.positives, negatives, counts.users, counts.courses)
    table = feature_table(rows, raw, course_part, events, args.popularity_window_days) if len(rows) else None
    report = association.analyze(table) if table is not None else None
    kept, dropped = association.select_features(
        [*features.CATEGORICAL_FEATURES, *features.NUMERIC_FEATURES], report.feature_tests if report else {})
    base = dict(counts=counts, negatives=negatives, report=report, kept=kept, dropped=dropped)

    decision = gate.check_sample(counts, gate.SampleThresholds(args.min_positive, args.min_users, args.min_courses))
    if not decision.passed:
        return Outcome(gate.RunResult.SKIPPED_INSUFFICIENT_DATA, message="; ".join(decision.shortfalls), **base)
    training = train_and_evaluate(table, kept, args.seed)
    if training is None:
        return Outcome(gate.RunResult.SKIPPED_INSUFFICIENT_DATA,
                       message="시간순 분할의 학습·검증 기간에 양성과 음성이 모두 있어야 합니다", **base)
    result = gate.decide_after_evaluation(training.chosen.precision_at_6, training.rules_precision)
    if result is not gate.RunResult.TRAINED:
        return Outcome(result, message="모델 precision@6 이 1차 규칙보다 낮습니다", training=training, **base)

    spec = features.fit_spec(table, training.categorical, training.numeric)
    final = model.train(training.chosen.model_type, features.encode(table, spec), table["label"], args.seed)
    scores = score_all(final, spec, raw, course_part, events, now, args)
    return Outcome(result, training=training, final_model=final, importance=model.feature_importance_top(final),
                   scores=scores, **base)


def write_report(outcome: Outcome, run_id: str, now: datetime, report_dir: Path) -> Path:
    report_dir.mkdir(parents=True, exist_ok=True)
    date = pd.Timestamp(now).tz_convert(dataset.SEOUL).strftime("%Y-%m-%d")
    training = outcome.training
    header = {"date": date, "run_id": run_id, "result": outcome.result.value, "positive": outcome.counts.positives,
              "negative": outcome.negatives, "users": outcome.counts.users, "courses": outcome.counts.courses,
              "message": outcome.message}
    detail = {
        "evaluations": [vars(e) for e in training.evaluations] if training else [],
        "rules_precision_at_6": training.rules_precision if training else None,
        "model_type": outcome.final_model.model_type if outcome.final_model else None,
        "feature_importance": outcome.importance,
    }
    path = report_dir / f"{date}-association.md"
    path.write_text(association.render_report(header, outcome.report, outcome.kept, outcome.dropped, detail),
                    encoding="utf-8")
    return path.resolve()


def to_record(outcome: Outcome, report_path: Path | None) -> db.RunRecord:
    chosen = outcome.training.chosen if outcome.training else None
    return db.RunRecord(
        result=outcome.result.value, finished_at=datetime.now(timezone.utc),
        model_type=chosen.model_type if chosen else None,
        positive_samples=outcome.counts.positives, negative_samples=outcome.negatives,
        user_count=outcome.counts.users, course_count=outcome.counts.courses,
        auc=chosen.auc if chosen else None, precision_at_6=chosen.precision_at_6 if chosen else None,
        rules_precision_at_6=outcome.training.rules_precision if outcome.training else None,
        feature_importance=outcome.importance or None,
        report_path=str(report_path) if report_path else None, message=outcome.message,
    )


def summary_line(run_id, result: str, record: db.RunRecord | None = None, score_rows: int = 0) -> str:
    payload = {"runId": run_id, "result": result, "exitCode": gate.exit_code_for(gate.RunResult(result))}
    if record is not None:
        payload.update({
            "modelType": record.model_type, "positiveSamples": record.positive_samples,
            "negativeSamples": record.negative_samples, "userCount": record.user_count,
            "courseCount": record.course_count, "auc": db._rounded(record.auc),
            "precisionAt6": db._rounded(record.precision_at_6),
            "rulesPrecisionAt6": db._rounded(record.rules_precision_at_6),
            "scoreRows": score_rows, "reportPath": record.report_path, "message": record.message,
        })
    elif result == gate.RunResult.FAILED.value:
        payload["message"] = "실패 — stderr 로그와 tb_recommendation_model_runs 를 확인하세요"
    return json.dumps(payload, ensure_ascii=False)


def execute(conn, args, run_id: uuid.UUID, started: datetime) -> tuple[db.RunRecord, int]:
    raw = db.read_all(conn)
    outcome = run_pipeline(raw, args, started)
    report_path = write_report(outcome, str(run_id), started, args.report_dir)
    record = to_record(outcome, report_path)
    if outcome.result is gate.RunResult.TRAINED:
        model_path = model.save_model(outcome.final_model, args.model_dir, str(run_id))
        LOG.info("모델 저장: %s", model_path)
        return record, db.finish_with_scores(conn, run_id, record, outcome.scores, started)
    db.finish_without_scores(conn, run_id, record)
    return record, 0


def _short_error(error: BaseException, settings) -> str:
    return db.redact(f"{type(error).__name__}: {error}", settings)[:300]


def main(argv=None) -> int:
    logging.basicConfig(stream=sys.stderr, level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    args = parse_args(argv)
    run_id, started = uuid.uuid4(), datetime.now(timezone.utc)
    try:
        settings = db.settings_from_env(os.environ)
        conn = db.connect(settings)
    except Exception as error:  # 접속 실패는 run 행을 쓸 수 없다
        LOG.error("DB 접속 실패: %s", _short_error(error, locals().get("settings")))
        print(summary_line(str(run_id), gate.RunResult.FAILED.value))
        return gate.EXIT_ERROR
    with conn:
        try:
            stale = db.fail_stale_runs(conn)
            if stale:
                LOG.warning("끝나지 않은 이전 run %d건을 FAILED 로 닫았습니다", stale)
            db.insert_run(conn, run_id, started)
            record, score_rows = execute(conn, args, run_id, started)
        except Exception as error:
            message = _short_error(error, settings)
            LOG.error("배치 실패: %s\n%s", message, db.redact(traceback.format_exc(), settings, limit=20_000))
            try:
                db.fail_run(conn, run_id, message)
            except Exception as nested:
                LOG.error("실패 기록도 못 했습니다: %s", _short_error(nested, settings))
            print(summary_line(str(run_id), gate.RunResult.FAILED.value))
            return gate.EXIT_ERROR
    print(summary_line(str(run_id), record.result, record, score_rows))
    return gate.exit_code_for(gate.RunResult(record.result))


if __name__ == "__main__":
    sys.exit(main())
