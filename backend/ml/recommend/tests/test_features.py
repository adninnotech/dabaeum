"""§12.3.2 feature 변환: 분류 3단계, 연령대(Java ageBand 와 같은 규칙), 기관 기타 묶기, 인코딩 정렬."""

import pandas as pd
import pytest

from recommend.features import (
    ALWAYS_KEEP,
    CATEGORICAL_FEATURES,
    FEATURE_GROUPS,
    NUMERIC_FEATURES,
    TOP_FACTOR_GROUPS,
    OTHER_INSTITUTION,
    FeatureSpec,
    age_band,
    age_years,
    column_groups,
    encode,
    fit_spec,
    group_institutions,
    interest_features,
    split_classification,
)


def test_분류_코드를_대중소_3단계로_나눈다():
    assert split_classification("TSIX_040102") == ("TSIX_04", "TSIX_0401", "TSIX_040102")


@pytest.mark.parametrize("code", [None, "", "BAD", float("nan")])
def test_분류_코드가_없거나_틀리면_UNKNOWN(code):
    assert split_classification(code) == ("UNKNOWN", "UNKNOWN", "UNKNOWN")


@pytest.mark.parametrize(
    ("years", "band"),
    [(0, "10"), (9, "10"), (15, "10"), (19, "10"), (20, "20"), (29, "20"), (65, "60"), (70, "70"), (95, "70")],
)
def test_연령대는_10세_단위이고_10에서_70으로_자른다(years, band):
    assert age_band(years) == band


def test_연령대는_나이가_없으면_UNKNOWN():
    assert age_band(None) == "UNKNOWN"


def test_만_나이는_생일이_지나야_한_살_더한다():
    births = pd.Series([pd.Timestamp("1960-05-10"), pd.Timestamp("1960-05-10"), pd.NaT])
    at = pd.Series(pd.to_datetime(["2020-05-09T12:00:00Z", "2020-05-10T12:00:00Z", "2020-05-10T12:00:00Z"]))
    years = age_years(births, at)
    assert years.iloc[0] == 59
    assert years.iloc[1] == 60
    assert pd.isna(years.iloc[2])


def test_강좌가_20개_미만인_기관은_기타로_묶는다():
    courses = pd.DataFrame({"institution_id": ["big"] * 20 + ["small"] * 19})
    mapping = group_institutions(courses, min_courses=20)
    assert mapping["big"] == "big"
    assert mapping["small"] == OTHER_INSTITUTION


def _interest_rows():
    rows = pd.DataFrame({"user_id": ["u1", "u1", "u2", "u3"]})
    class_l1 = pd.Series(["TSIX_04", "TSIX_05", "TSIX_04", "TSIX_04"])
    interests = pd.DataFrame({"user_id": ["u1", "u1", "u2"],
                              "classification_code": ["TSIX_040000", "TSIX_020000", "TSIX_050000"]})
    return rows, class_l1, interests


def test_관심_분야에_강좌_대분류가_있으면_interest_match_1():
    rows, class_l1, interests = _interest_rows()
    result = interest_features(rows, class_l1, interests)
    assert result["interest_match"].tolist() == [1, 0, 0, 0]


def test_관심_분야가_하나라도_있으면_has_interest_1이고_없는_회원은_둘_다_0():
    rows, class_l1, interests = _interest_rows()
    result = interest_features(rows, class_l1, interests)
    assert result["has_interest"].tolist() == [1, 1, 1, 0]
    assert result.loc[3, "interest_match"] == 0


def test_관심_분야_표가_비어_있어도_동작한다():
    rows, class_l1, _ = _interest_rows()
    empty = pd.DataFrame({"user_id": [], "classification_code": []})
    result = interest_features(rows, class_l1, empty)
    assert result["interest_match"].sum() == 0 and result["has_interest"].sum() == 0


def test_지역_feature_는_없고_관심_feature_는_INTEREST_묶음이며_항상_유지한다():
    assert "REGION" not in TOP_FACTOR_GROUPS and "INTEREST" in TOP_FACTOR_GROUPS
    assert FEATURE_GROUPS["interest_match"] == "INTEREST" and FEATURE_GROUPS["has_interest"] == "INTEREST"
    assert {"interest_match", "has_interest"} <= ALWAYS_KEEP
    names = {*CATEGORICAL_FEATURES, *NUMERIC_FEATURES}
    assert not {"region_code", "region_match", "pop_region"} & names
    assert {"interest_match", "has_interest"} <= set(NUMERIC_FEATURES)


def test_학습에_없던_범주는_모든_더미가_0이고_열_순서는_같다():
    train = pd.DataFrame({"age_band": ["20", "60", "60"], "pop_all": [1.0, 2.0, 3.0]})
    spec = fit_spec(train, categorical=("age_band",), numeric=("pop_all",))
    scoring = pd.DataFrame({"age_band": ["70"], "pop_all": [5.0]})
    encoded = encode(scoring, spec)
    assert list(encoded.columns) == list(encode(train, spec).columns)
    assert encoded.filter(like="age_band=").to_numpy().sum() == 0
    assert encoded["pop_all"].iloc[0] == 5.0


def test_인코딩된_열은_topFactor_묶음으로_돌아간다():
    spec = FeatureSpec(
        categorical={"class_l1": ("TSIX_04",), "gender": ("MALE",)},
        numeric=("pop_all", "user_ratio_TSIX_05", "tuition_amount"),
    )
    groups = column_groups(spec.columns)
    assert groups["class_l1=TSIX_04"] == "CLASSIFICATION"
    assert groups["user_ratio_TSIX_05"] == "CLASSIFICATION"
    assert groups["pop_all"] == "POPULARITY"
    assert groups["tuition_amount"] == "PRICE"
    assert groups["gender=MALE"] is None
