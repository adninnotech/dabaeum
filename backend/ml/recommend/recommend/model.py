"""§12.4 모델: XGBoost(기본)·RandomForest(비교) 학습, AUC·회원당 precision@6, 모델 고르기, 설명(중요도·topFactor)."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import roc_auc_score

from .features import TOP_FACTOR_GROUPS, base_feature, column_groups, group_of

MODEL_XGBOOST = "XGBOOST"
MODEL_RANDOM_FOREST = "RANDOM_FOREST"
PRECISION_K = 6
# RandomForest 가 precision@6 에서 이만큼 넘게 앞설 때만 바꾼다. 비슷하면 XGBoost(§12.4).
SIMILAR_MARGIN = 0.01
XGB_PARAMS = {"objective": "binary:logistic", "max_depth": 6, "n_estimators": 300, "learning_rate": 0.1,
              "tree_method": "hist", "eval_metric": "auc"}
RF_PARAMS = {"n_estimators": 300, "min_samples_leaf": 5, "n_jobs": -1}


@dataclass(frozen=True)
class ModelEvaluation:
    model_type: str
    auc: float | None
    precision_at_6: float | None


@dataclass(frozen=True)
class TrainedModel:
    model_type: str
    estimator: object
    columns: tuple
    means: np.ndarray
    stds: np.ndarray


def precision_at_k(frame: pd.DataFrame, k: int = PRECISION_K) -> float | None:
    """회원별로 score 상위 k 건 중 신청(label=1) 비율의 평균. 검증 기간에 신청이 있는 회원만 센다.

    후보가 k 건보다 적은 회원은 후보 수로 나눈다(홈에 그만큼만 보이므로).
    """
    users_with_positive = frame.groupby("user_id")["label"].transform("max") > 0
    scoped = frame[users_with_positive]
    if scoped.empty:
        return None
    ordered = scoped.sort_values(["user_id", "score"], ascending=[True, False], kind="stable")
    top = ordered[ordered.groupby("user_id").cumcount() < k]
    per_user = top.groupby("user_id")["label"].agg(["sum", "count"])
    return float((per_user["sum"] / per_user["count"]).mean())


def train(model_type: str, features: pd.DataFrame, labels: pd.Series, seed: int) -> TrainedModel:
    if model_type == MODEL_XGBOOST:
        from xgboost import XGBClassifier  # 네이티브 라이브러리(OpenMP)를 쓰므로 학습할 때만 불러온다

        estimator = XGBClassifier(**XGB_PARAMS, random_state=seed)
    else:
        estimator = RandomForestClassifier(**RF_PARAMS, random_state=seed)
    estimator.fit(features, labels.astype(int))
    values = features.to_numpy(dtype=float)
    stds = values.std(axis=0)
    return TrainedModel(model_type, estimator, tuple(features.columns), values.mean(axis=0),
                        np.where(stds > 0, stds, 1.0))


def predict(trained: TrainedModel, features: pd.DataFrame) -> np.ndarray:
    return trained.estimator.predict_proba(features[list(trained.columns)])[:, 1]


def auc_or_none(labels, scores) -> float | None:
    labels = np.asarray(labels)
    if len(np.unique(labels)) < 2:
        return None
    return float(roc_auc_score(labels, scores))


def evaluate(trained: TrainedModel, features: pd.DataFrame, valid: pd.DataFrame) -> ModelEvaluation:
    scores = predict(trained, features)
    frame = pd.DataFrame({"user_id": valid["user_id"].to_numpy(), "label": valid["label"].to_numpy(), "score": scores})
    return ModelEvaluation(trained.model_type, auc_or_none(frame["label"], scores), precision_at_k(frame))


def choose_model(xgb: ModelEvaluation, rf: ModelEvaluation, margin: float = SIMILAR_MARGIN) -> ModelEvaluation:
    if xgb.precision_at_6 is None:
        return rf
    if rf.precision_at_6 is not None and rf.precision_at_6 > xgb.precision_at_6 + margin:
        return rf
    return xgb


def _raw_importance(trained: TrainedModel) -> pd.Series:
    if trained.model_type == MODEL_XGBOOST:
        gains = trained.estimator.get_booster().get_score(importance_type="gain")
        return pd.Series({column: gains.get(column, 0.0) for column in trained.columns}, dtype=float)
    return pd.Series(trained.estimator.feature_importances_, index=list(trained.columns), dtype=float)


def feature_importance_top(trained: TrainedModel, top: int = 10) -> list[dict]:
    """원 feature 단위(one-hot 더미는 합산)로 정규화한 중요도 상위 top 개."""
    raw = _raw_importance(trained)
    by_feature = raw.groupby([base_feature(c) for c in raw.index]).sum()
    total = by_feature.sum()
    normalized = (by_feature / total) if total > 0 else by_feature
    best = normalized.sort_values(ascending=False).head(top)
    return [{"feature": name, "group": group_of(name), "importance": round(float(value), 6)}
            for name, value in best.items()]


def contributions(trained: TrainedModel, features: pd.DataFrame) -> np.ndarray:
    """행별 열 기여도. XGBoost 는 pred_contribs(SHAP 값), RandomForest 는 importance × 표준화 값(근사)."""
    matrix = features[list(trained.columns)]
    if trained.model_type == MODEL_XGBOOST:
        from xgboost import DMatrix

        booster = trained.estimator.get_booster()
        return booster.predict(DMatrix(matrix), pred_contribs=True)[:, :-1]
    standardized = (matrix.to_numpy(dtype=float) - trained.means) / trained.stds
    return standardized * trained.estimator.feature_importances_


def top_factors(trained: TrainedModel, features: pd.DataFrame) -> np.ndarray:
    """기여도를 topFactor 묶음별로 더해 가장 큰 묶음 이름을 행마다 돌려준다."""
    if features.empty:
        return np.array([], dtype=object)
    contrib = contributions(trained, features)
    groups = column_groups(trained.columns)
    sums = np.full((len(features), len(TOP_FACTOR_GROUPS)), -np.inf)
    for j, group in enumerate(TOP_FACTOR_GROUPS):
        members = [i for i, column in enumerate(trained.columns) if groups[column] == group]
        if members:
            sums[:, j] = contrib[:, members].sum(axis=1)
    return np.array(TOP_FACTOR_GROUPS, dtype=object)[np.argmax(sums, axis=1)]


def save_model(trained: TrainedModel, model_dir: Path, run_id: str) -> Path:
    model_dir.mkdir(parents=True, exist_ok=True)
    if trained.model_type == MODEL_XGBOOST:
        path = model_dir / f"{run_id}.ubj"
        trained.estimator.save_model(path)
    else:
        path = model_dir / f"{run_id}.joblib"
        joblib.dump(trained.estimator, path)
    return path
