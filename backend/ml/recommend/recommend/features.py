"""§12.3.2 feature 표: 범주 처리, 분류 코드 3단계, 연령대, 기관 기타 묶기, 인코딩, topFactor 묶음."""

from __future__ import annotations

import re
from dataclasses import dataclass, field

import numpy as np
import pandas as pd

UNKNOWN = "UNKNOWN"
OTHER_INSTITUTION = "OTHER"
MIN_INSTITUTION_COURSES = 20
SEOUL = "Asia/Seoul"

MAJOR_CODES = tuple(f"TSIX_0{i}" for i in range(1, 8))
TARGET_AUDIENCES = (
    "ANYONE", "CHILDREN", "GUARDIANS_FAMILY", "YOUTH", "ADULTS", "MIDDLE_AGED_SENIORS", "MEN", "WOMEN",
)
_CLASSIFICATION_PATTERN = re.compile(r"^TSIX_[0-9]{6}$")
_WEEKDAYS = ("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

CATEGORICAL_FEATURES = (
    "class_l1", "class_l2", "class_l3", "education_type", "tuition_type", "material_fee_type",
    "first_weekday", "first_time_band", "institution", "age_band", "gender",
)
NUMERIC_FEATURES = (
    "tuition_amount", "credit_bank", "capacity", "recruit_days", "session_count",
    *(f"target_{code}" for code in TARGET_AUDIENCES),
    "days_since_signup", "interest_match", "has_interest",
    "interest_count", "enrollment_count", "completion_count", "days_since_last_action", "user_paid_ratio",
    "match_level", "same_major_ratio", "same_institution_before",
    *(f"user_ratio_{code}" for code in MAJOR_CODES),
    "pop_all", "pop_age_band",
)
# 통계와 무관하게 유지하는 feature(§12.3.3 선정 규칙의 예외).
ALWAYS_KEEP = frozenset({"class_l1", "class_l2", "class_l3", "match_level", "interest_match", "has_interest"})

# topFactor 묶음(V55 CHECK). 여기 없는 feature 는 점수에는 쓰이지만 topFactor 후보가 아니다.
TOP_FACTOR_GROUPS = ("CLASSIFICATION", "INTEREST", "AGE_BAND", "PRICE", "SCHEDULE", "POPULARITY")
FEATURE_GROUPS = {
    "class_l1": "CLASSIFICATION", "class_l2": "CLASSIFICATION", "class_l3": "CLASSIFICATION",
    "match_level": "CLASSIFICATION", "same_major_ratio": "CLASSIFICATION",
    "age_band": "AGE_BAND",
    "interest_match": "INTEREST", "has_interest": "INTEREST",
    "tuition_type": "PRICE", "tuition_amount": "PRICE", "material_fee_type": "PRICE", "user_paid_ratio": "PRICE",
    "education_type": "SCHEDULE", "session_count": "SCHEDULE", "first_weekday": "SCHEDULE",
    "first_time_band": "SCHEDULE", "recruit_days": "SCHEDULE",
    "pop_all": "POPULARITY", "pop_age_band": "POPULARITY",
}


def split_classification(code) -> tuple[str, str, str]:
    """TSIX_AABBCC → (대 TSIX_AA, 중 TSIX_AABB, 소 TSIX_AABBCC). §3.2 의 substr(1,7)·substr(1,9) 와 같다."""
    if not isinstance(code, str) or not _CLASSIFICATION_PATTERN.match(code):
        return (UNKNOWN, UNKNOWN, UNKNOWN)
    return (code[:7], code[:9], code)


def age_band(years) -> str:
    """Java CourseRecommendationService.ageBand 와 같다: clamp((years/10)*10, 10, 70)."""
    if years is None or (isinstance(years, float) and np.isnan(years)) or pd.isna(years):
        return UNKNOWN
    band = min(max((int(years) // 10) * 10, 10), 70)
    return str(band)


def age_years(birth_dates: pd.Series, at: pd.Series) -> pd.Series:
    """기준 시각(서울 날짜)의 만 나이. 생년월일이 없으면 NaN."""
    births = pd.to_datetime(birth_dates.reset_index(drop=True))
    days = pd.to_datetime(at.reset_index(drop=True), utc=True).dt.tz_convert(SEOUL)
    years = days.dt.year - births.dt.year
    before_birthday = (days.dt.month < births.dt.month) | (
        (days.dt.month == births.dt.month) & (days.dt.day < births.dt.day)
    )
    return (years - before_birthday.astype(int)).where(births.notna()).astype("float")


def group_institutions(courses: pd.DataFrame, min_courses: int = MIN_INSTITUTION_COURSES) -> dict:
    """강좌가 min_courses 개 미만인 기관은 OTHER 로 묶는다(§12.3.2)."""
    counts = courses["institution_id"].value_counts()
    return {inst: (inst if count >= min_courses else OTHER_INSTITUTION) for inst, count in counts.items()}


def _time_band(hours: pd.Series) -> pd.Series:
    bands = np.select([hours < 12, hours < 18, hours >= 18], ["MORNING", "AFTERNOON", "EVENING"], default="NONE")
    return pd.Series(bands, index=hours.index).where(hours.notna(), "NONE")


def course_features(courses: pd.DataFrame, institution_map: dict) -> pd.DataFrame:
    """강좌 속성 feature. course_id 를 인덱스로 둔다."""
    levels = courses["classification_code"].map(split_classification)
    first = pd.to_datetime(courses["first_session_at"], utc=True).dt.tz_convert(SEOUL)
    audiences = courses["target_audiences"].fillna("").astype(str).str.split(",")
    recruit_days = (
        pd.to_datetime(courses["recruit_end_date"]) - pd.to_datetime(courses["recruit_start_date"])
    ).dt.days
    frame = pd.DataFrame(
        {
            "course_id": courses["course_id"],
            "class_l1": [lv[0] for lv in levels],
            "class_l2": [lv[1] for lv in levels],
            "class_l3": [lv[2] for lv in levels],
            "education_type": courses["education_type"].fillna(UNKNOWN),
            "tuition_type": courses["tuition_type"].fillna("FREE"),
            "tuition_amount": courses["tuition_amount"].where(courses["tuition_type"] == "PAID", 0).fillna(0),
            "material_fee_type": courses["material_fee_type"].fillna("FREE"),
            "credit_bank": courses["credit_bank_eligible"].fillna(False).astype(int),
            "capacity": courses["capacity"].fillna(0),
            "recruit_days": recruit_days.fillna(-1),
            "session_count": courses["session_count"].fillna(0),
            "first_weekday": first.dt.dayofweek.map(lambda d: _WEEKDAYS[int(d)] if pd.notna(d) else "NONE"),
            "first_time_band": _time_band(first.dt.hour.astype("float")),
            "institution": courses["institution_id"].map(institution_map).fillna(OTHER_INSTITUTION),
        }
    )
    for code in TARGET_AUDIENCES:
        frame[f"target_{code}"] = audiences.map(lambda values, c=code: int(c in values))
    return frame.set_index("course_id")


def user_features_at(rows: pd.DataFrame, users: pd.DataFrame) -> pd.DataFrame:
    """회원 속성(기준 시각 rows.event_at 기준): 연령대, 성별, 가입 후 일수."""
    joined = rows[["user_id", "event_at"]].reset_index(drop=True).merge(
        users[["user_id", "birth_date", "gender", "created_at"]], on="user_id", how="left"
    )
    years = age_years(joined["birth_date"], joined["event_at"])
    signup = (pd.to_datetime(joined["event_at"], utc=True) - pd.to_datetime(joined["created_at"], utc=True))
    return pd.DataFrame(
        {
            "age_years": years,
            "age_band": years.map(age_band),
            "gender": joined["gender"].fillna(UNKNOWN),
            "days_since_signup": (signup.dt.total_seconds() / 86400).clip(lower=0).fillna(0),
        }
    )


def assemble(rows: pd.DataFrame, user_part: pd.DataFrame, course_part: pd.DataFrame,
             behaviour: pd.DataFrame, popularity: pd.DataFrame) -> pd.DataFrame:
    """행 순서를 유지한 원 feature 표. 모든 부분 표는 rows 와 같은 순서·길이다(course_part 는 course_id 로 붙인다)."""
    base = rows.reset_index(drop=True)
    courses = course_part.reindex(base["course_id"]).reset_index(drop=True)
    frame = pd.concat([base, user_part.reset_index(drop=True), courses,
                       behaviour.reset_index(drop=True), popularity.reset_index(drop=True)], axis=1)
    return frame


def interest_features(rows: pd.DataFrame, class_l1: pd.Series, user_interests: pd.DataFrame) -> pd.DataFrame:
    """회원 관심 분야 feature: interest_match(강좌 대분류가 관심 분야에 있음), has_interest(관심 분야가 하나라도 있음).

    주의: 관심 분야는 현재 상태 하나뿐이라(tb_user_interests.created_at 은 입력 시각일 뿐 이력이 아니다)
    과거 행에도 현재 값을 그대로 쓴다. 가상 데이터는 관심 분야를 신청 이력에서 만들 수 있어
    이 feature 때문에 성능 수치가 부풀 수 있다. 실제 성능으로 해석하지 않는다.
    """
    wanted = {}
    for user_id, code in zip(user_interests["user_id"], user_interests["classification_code"]):
        wanted.setdefault(str(user_id), set()).add(split_classification(code)[0])
    users = rows["user_id"].astype(str).to_numpy()
    majors = class_l1.astype(str).to_numpy()
    has = np.array([int(user in wanted) for user in users], dtype=int)
    match = np.array([int(major in wanted.get(user, ())) for user, major in zip(users, majors)], dtype=int)
    return pd.DataFrame({"interest_match": match, "has_interest": has}, index=rows.index)


@dataclass(frozen=True)
class FeatureSpec:
    """학습 때 정한 범주 목록과 수치 열. 점수 산출 때 같은 열 순서를 만든다."""

    categorical: dict = field(default_factory=dict)
    numeric: tuple = ()

    @property
    def columns(self) -> list[str]:
        dummies = [f"{name}={value}" for name, values in self.categorical.items() for value in values]
        return dummies + list(self.numeric)

    @property
    def base_features(self) -> list[str]:
        return list(self.categorical) + list(self.numeric)


def fit_spec(raw: pd.DataFrame, categorical, numeric) -> FeatureSpec:
    values = {name: tuple(sorted(raw[name].astype(str).unique())) for name in categorical}
    return FeatureSpec(categorical=values, numeric=tuple(numeric))


def encode(raw: pd.DataFrame, spec: FeatureSpec) -> pd.DataFrame:
    """one-hot(학습에 없던 범주는 모든 더미 0) + 수치. 열 순서는 spec.columns."""
    parts = {}
    for name, values in spec.categorical.items():
        column = raw[name].astype(str).to_numpy()
        for value in values:
            parts[f"{name}={value}"] = (column == value).astype(np.float32)
    for name in spec.numeric:
        parts[name] = pd.to_numeric(raw[name], errors="coerce").fillna(0).to_numpy(dtype=np.float32)
    return pd.DataFrame(parts, index=raw.index)[spec.columns]


def base_feature(column: str) -> str:
    return column.split("=", 1)[0]


def group_of(feature: str) -> str | None:
    if feature.startswith("user_ratio_"):
        return "CLASSIFICATION"
    return FEATURE_GROUPS.get(feature)


def column_groups(columns) -> dict:
    return {column: group_of(base_feature(column)) for column in columns}
