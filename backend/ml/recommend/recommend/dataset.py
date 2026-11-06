"""§12.3.1 타깃·음성 추출, 시점 누출 방지(기준 시각 이전 행동만), 시간순 분할, 점수 산출 후보.

모든 시각은 UTC tz-aware pandas Timestamp 로 다룬다. 날짜 판단(모집 기간·나이)은 서울 날짜로 한다.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from .features import MAJOR_CODES, UNKNOWN, age_years, split_classification

SEOUL = "Asia/Seoul"
NEGATIVE_RATIO = 4
ENROLLMENT_WEIGHT = 3          # PopularityProperties.enrollmentWeight 기본값
NO_ACTION_DAYS = 3650.0        # 이전 행동이 없을 때 "마지막 행동 후 일수"
ADULT_AGE = 19                 # CourseRecommendationService.ADULT_AGE
CHILD_ONLY_AUDIENCES = frozenset({"CHILDREN", "YOUTH"})
_NS_PER_DAY = 86_400 * 1_000_000_000
_KEY_SHIFT = 2 ** 33           # 합성 키: 키 번호 × 2^33 + 초(약 272년 범위)

BEHAVIOUR_COLUMNS = (
    "interest_count", "enrollment_count", "completion_count", "days_since_last_action", "user_paid_ratio",
    "match_level", "same_major_ratio", "same_institution_before",
    *(f"user_ratio_{code}" for code in MAJOR_CODES),
)
ROW_COLUMNS = ["user_id", "course_id", "event_at", "label", "source"]


def _utc(values) -> pd.Series:
    return pd.to_datetime(values, utc=True)


def _seoul_dates(values) -> pd.Series:
    return _utc(values).dt.tz_convert(SEOUL).dt.tz_localize(None).dt.normalize()


def build_positives(enrollments: pd.DataFrame, users: pd.DataFrame) -> pd.DataFrame:
    """양성 = 신청·대기·승인 또는 수료(db 가 positive 로 표시). 회원·강좌 쌍마다 가장 이른 신청 1건."""
    active = enrollments[enrollments["positive"].astype(bool) & enrollments["user_id"].isin(users["user_id"])]
    first = active.groupby(["user_id", "course_id"], as_index=False)["applied_at"].min()
    rows = first.rename(columns={"applied_at": "event_at"}).assign(label=1, source="ENROLLMENT")
    rows["event_at"] = _utc(rows["event_at"])
    return rows[ROW_COLUMNS].sort_values(["event_at", "user_id"]).reset_index(drop=True)


def impression_negatives(impressions: pd.DataFrame, enrollments: pd.DataFrame, users: pd.DataFrame) -> pd.DataFrame:
    """노출됐지만 신청 이력이 없는 (회원, 강좌). 쌍마다 처음 노출된 시각."""
    enrolled = set(zip(enrollments["user_id"], enrollments["course_id"]))
    shown = impressions[impressions["user_id"].isin(users["user_id"])]
    keep = np.array([pair not in enrolled for pair in zip(shown["user_id"], shown["course_id"])], dtype=bool)
    first = shown[keep].groupby(["user_id", "course_id"], as_index=False)["shown_at"].min()
    rows = first.rename(columns={"shown_at": "event_at"}).assign(label=0, source="IMPRESSION")
    rows["event_at"] = _utc(rows["event_at"])
    return rows[ROW_COLUMNS].reset_index(drop=True)


def _open_mask(courses: pd.DataFrame, day: pd.Timestamp) -> np.ndarray:
    start = pd.to_datetime(courses["recruit_start_date"])
    end = pd.to_datetime(courses["recruit_end_date"])
    return ((start.isna() | (start <= day)) & (end.isna() | (end >= day))).to_numpy()


def random_negatives(positives: pd.DataFrame, courses: pd.DataFrame, excluded_pairs: set,
                     ratio: int = NEGATIVE_RATIO, rng: np.random.Generator | None = None) -> pd.DataFrame:
    """양성 1건마다 그 신청 시각(서울 날짜)에 모집 중이던 강좌 중 회원이 신청·찜하지 않은 것을 ratio 건 뽑는다."""
    rng = rng or np.random.default_rng()
    course_ids = courses["course_id"].to_numpy()
    days = _seoul_dates(positives["event_at"])
    masks = {day: _open_mask(courses, day) for day in days.unique()}
    picked_users, picked_courses, picked_times = [], [], []
    for user, event_at, day in zip(positives["user_id"], positives["event_at"], days):
        candidates = course_ids[masks[day]]
        candidates = np.array([c for c in candidates if (user, c) not in excluded_pairs], dtype=object)
        if len(candidates) == 0:
            continue
        chosen = rng.choice(candidates, size=min(ratio, len(candidates)), replace=False)
        picked_users += [user] * len(chosen)
        picked_courses += list(chosen)
        picked_times += [event_at] * len(chosen)
    rows = pd.DataFrame({"user_id": picked_users, "course_id": picked_courses,
                         "event_at": _utc(pd.Series(picked_times, dtype="object"))})
    return rows.assign(label=0, source="RANDOM")[ROW_COLUMNS]


def build_training_rows(enrollments, interests, impressions, users, courses, rng, ratio=NEGATIVE_RATIO):
    positives = build_positives(enrollments, users)
    excluded = set(zip(enrollments["user_id"], enrollments["course_id"])) | set(
        zip(interests["user_id"], interests["course_id"]))
    random_rows = random_negatives(positives, courses, excluded, ratio=ratio, rng=rng)
    shown_rows = impression_negatives(impressions, enrollments, users)
    rows = pd.concat([positives, shown_rows, random_rows], ignore_index=True)
    return rows.drop_duplicates(["user_id", "course_id", "event_at"]).sort_values("event_at").reset_index(drop=True)


def build_events(interests: pd.DataFrame, enrollments: pd.DataFrame, courses: pd.DataFrame) -> pd.DataFrame:
    """회원 행동(찜·신청·수료)을 한 표로. 시점 누출 방지 계산의 입력이다."""
    positive = enrollments[enrollments["positive"].astype(bool)]
    completed = positive[positive["completed_at"].notna()]
    parts = [
        pd.DataFrame({"user_id": interests["user_id"], "course_id": interests["course_id"],
                      "at": _utc(interests["created_at"]), "kind": 0}),
        pd.DataFrame({"user_id": positive["user_id"], "course_id": positive["course_id"],
                      "at": _utc(positive["applied_at"]), "kind": 1}),
        pd.DataFrame({"user_id": completed["user_id"], "course_id": completed["course_id"],
                      "at": _utc(completed["completed_at"]), "kind": 2}),
    ]
    events = pd.concat(parts, ignore_index=True)
    events = events.astype({"user_id": "object", "course_id": "object", "kind": "int64"})
    attrs = courses[["course_id", "classification_code", "institution_id", "tuition_type"]]
    events = events.merge(attrs, on="course_id", how="left")
    events["paid"] = (events["tuition_type"] == "PAID").astype(int)
    return events.sort_values("at", kind="stable").reset_index(drop=True)


def _levels(codes) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    split = [split_classification(code) for code in codes]
    l1 = np.array([s[0] for s in split], dtype=object)
    known = l1 != UNKNOWN
    return l1, np.array([s[1] for s in split], dtype=object), np.array([s[2] for s in split], dtype=object), known


def _ns(values) -> np.ndarray:
    return _utc(values).to_numpy(dtype="datetime64[ns]").astype(np.int64)


class _UserEvents:
    """한 회원의 행동을 시각 순으로 담고, 기준 시각 이전 누적값을 바로 꺼내게 한다."""

    def __init__(self, events: pd.DataFrame):
        self.at = _ns(events["at"])
        kind = events["kind"].to_numpy(dtype=int)
        self.l1, self.l2, self.l3, self.known = _levels(events["classification_code"])
        self.institution = events["institution_id"].to_numpy(dtype=object)
        self.is_enrollment = kind >= 1
        prepend = lambda values: np.concatenate([[0], np.cumsum(values, axis=0)])  # noqa: E731
        self.cum_interest = prepend(kind == 0)
        self.cum_enroll = prepend(kind == 1)
        self.cum_complete = prepend(kind == 2)
        self.cum_paid = prepend((kind == 1) & (events["paid"].to_numpy() == 1))
        majors = np.array([[l1 == code for code in MAJOR_CODES] for l1 in self.l1], dtype=float).reshape(-1, 7)
        self.cum_major = np.vstack([np.zeros((1, 7)), np.cumsum(majors, axis=0)])


def _fill_user(out: dict, idx: np.ndarray, times: np.ndarray, codes, institutions, ev: _UserEvents) -> None:
    k = np.searchsorted(ev.at, times, side="left")  # 기준 시각과 같은 행동(자기 신청)은 넣지 않는다
    out["interest_count"][idx] = ev.cum_interest[k]
    out["completion_count"][idx] = ev.cum_complete[k]
    enrolled = ev.cum_enroll[k]
    out["enrollment_count"][idx] = enrolled
    out["user_paid_ratio"][idx] = ev.cum_paid[k] / np.maximum(enrolled, 1)
    last = np.where(k > 0, ev.at[np.maximum(k - 1, 0)], 0)
    out["days_since_last_action"][idx] = np.where(k > 0, (times - last) / _NS_PER_DAY, NO_ACTION_DAYS)

    ratios = ev.cum_major[k] / np.maximum(ev.cum_major[k].sum(axis=1, keepdims=True), 1)
    top3 = np.argsort(-ratios, axis=1)[:, :3]
    kept = np.zeros_like(ratios)
    np.put_along_axis(kept, top3, np.take_along_axis(ratios, top3, axis=1), axis=1)
    for j, code in enumerate(MAJOR_CODES):
        out[f"user_ratio_{code}"][idx] = kept[:, j]

    r1, r2, r3, row_known = _levels(codes)
    earlier = np.arange(len(ev.at))[None, :] < k[:, None]
    before = earlier & ev.known[None, :] & row_known[:, None]
    level = np.where(r3[:, None] == ev.l3[None, :], 3,
                     np.where(r2[:, None] == ev.l2[None, :], 2, np.where(r1[:, None] == ev.l1[None, :], 1, 0)))
    out["match_level"][idx] = np.where(before, level, 0).max(axis=1, initial=0)
    major_index = np.array([MAJOR_CODES.index(c) if c in MAJOR_CODES else -1 for c in r1])
    out["same_major_ratio"][idx] = np.where(major_index >= 0, ratios[np.arange(len(k)), np.maximum(major_index, 0)], 0)
    same_inst = (np.asarray(institutions, dtype=object)[:, None] == ev.institution[None, :]) & ev.is_enrollment[None, :]
    out["same_institution_before"][idx] = (same_inst & earlier).any(axis=1)


def behaviour_before(rows: pd.DataFrame, events: pd.DataFrame) -> pd.DataFrame:
    """각 행(user_id, event_at, classification_code, institution_id)의 회원 행동 feature. event_at 보다 엄격히 이전 행동만 쓴다."""
    rows = rows.reset_index(drop=True)
    out = {name: np.zeros(len(rows), dtype=float) for name in BEHAVIOUR_COLUMNS}
    out["days_since_last_action"][:] = NO_ACTION_DAYS
    if len(rows) and len(events):
        times = _ns(rows["event_at"])
        codes = rows["classification_code"].to_numpy(dtype=object)
        institutions = rows["institution_id"].to_numpy(dtype=object)
        event_groups = events.groupby("user_id").indices
        for user, idx in rows.groupby("user_id").indices.items():
            if user in event_groups:
                ev = _UserEvents(events.iloc[event_groups[user]].sort_values("at", kind="stable"))
                _fill_user(out, idx, times[idx], codes[idx], institutions[idx], ev)
    return pd.DataFrame(out)


def _window_counts(row_keys, row_times, event_keys, event_times, window_ns: int) -> np.ndarray:
    """키가 같은 행동 중 [기준 - 창, 기준) 안에 든 건수."""
    if len(event_keys) == 0 or len(row_keys) == 0:
        return np.zeros(len(row_keys))
    codes, uniques = pd.factorize(pd.Series(list(event_keys), dtype="object"))
    lookup = {key: i for i, key in enumerate(uniques)}
    base = min(int(np.min(event_times)), int(np.min(row_times))) - window_ns
    to_s = lambda ns: (np.asarray(ns, dtype=np.int64) - base) // 1_000_000_000  # noqa: E731
    composite = np.sort(codes.astype(np.int64) * _KEY_SHIFT + to_s(event_times))
    row_codes = np.array([lookup.get(key, len(uniques)) for key in row_keys], dtype=np.int64) * _KEY_SHIFT
    upper = np.searchsorted(composite, row_codes + to_s(row_times), side="left")
    lower = np.searchsorted(composite, row_codes + to_s(np.asarray(row_times) - window_ns), side="left")
    return (upper - lower).astype(float)


def popularity_at(rows: pd.DataFrame, pop_events: pd.DataFrame, window_days: int) -> pd.DataFrame:
    """인기 점수(§3.3 과 같은 정의: 최근 window_days 일 신청 × 3)를 행의 기준 시각마다 다시 센다.

    tb_course_popularity 는 지금 시점 스냅샷이라 과거 행에 붙이면 그 행의 신청 자신이 섞인다(시점 누출).
    조회 수는 시점별로 다시 셀 수 없어(익명 조회자 하루 1건) 넣지 않는다.
    """
    rows = rows.reset_index(drop=True)
    window = window_days * _NS_PER_DAY
    times = _ns(rows["event_at"])
    ev_times = _ns(pop_events["applied_at"]) if len(pop_events) else np.array([], dtype=np.int64)
    course = rows["course_id"].astype(str).to_numpy()
    ev_course = pop_events["course_id"].astype(str).to_numpy()
    segments = {
        "pop_all": (course, ev_course),
        "pop_age_band": (course + "|" + rows["age_band"].astype(str).to_numpy(),
                         ev_course + "|" + pop_events["age_band"].fillna(UNKNOWN).astype(str).to_numpy()),
    }
    return pd.DataFrame({name: ENROLLMENT_WEIGHT * _window_counts(rk, times, ek, ev_times, window)
                         for name, (rk, ek) in segments.items()})


def time_split(rows: pd.DataFrame, valid_fraction: float = 0.2) -> tuple[pd.Index, pd.Index]:
    """시간순 분할: 전체 기간의 마지막 valid_fraction 을 검증으로. 무작위 분할은 같은 회원이 양쪽에 들어가 과대평가된다."""
    times = _utc(rows["event_at"])
    start, end = times.min(), times.max()
    cutoff = start + (end - start) * (1 - valid_fraction)
    valid = times >= cutoff
    return rows.index[~valid.to_numpy()], rows.index[valid.to_numpy()]


def recruiting_courses(courses: pd.DataFrame, today: pd.Timestamp) -> pd.DataFrame:
    """§3.4 의 강좌 조건: 모집 중, 모집 기간 안, 정원+대기 남음, 분류 코드 있음."""
    capacity = courses["capacity"]
    room = capacity.isna() | (capacity + courses["waitlist_capacity"].fillna(0) > courses["active_count"].fillna(0))
    known = courses["classification_code"].map(lambda c: split_classification(c)[0] != UNKNOWN)
    mask = (courses["status"] == "RECRUITING") & _open_mask(courses, today) & room & known
    return courses[mask.to_numpy()].reset_index(drop=True)


def _child_only(target_audiences) -> bool:
    if not isinstance(target_audiences, str) or not target_audiences.strip():
        return False
    return set(target_audiences.split(",")) <= CHILD_ONLY_AUDIENCES


def scoring_rows(users: pd.DataFrame, courses: pd.DataFrame, excluded_pairs: set, now: pd.Timestamp) -> pd.DataFrame:
    """활성 학습자 × 모집 중 강좌. 신청·찜한 강좌와 성인에게 어린이·청소년 전용 강좌는 뺀다(§3.4 와 같다)."""
    if users.empty or courses.empty:
        return pd.DataFrame(columns=["user_id", "course_id", "event_at"])
    at = pd.Series([now] * len(users))
    adult = (age_years(users["birth_date"], at) >= ADULT_AGE).to_numpy()
    child_only = courses["target_audiences"].map(_child_only).to_numpy()
    user_ids = users["user_id"].to_numpy()
    course_ids = courses["course_id"].to_numpy()
    grid_users = np.repeat(user_ids, len(course_ids))
    grid_courses = np.tile(course_ids, len(user_ids))
    keep = ~(np.repeat(adult, len(course_ids)) & np.tile(child_only, len(user_ids)))
    keep &= np.array([pair not in excluded_pairs for pair in zip(grid_users, grid_courses)], dtype=bool)
    return pd.DataFrame({"user_id": grid_users[keep], "course_id": grid_courses[keep], "event_at": now})
