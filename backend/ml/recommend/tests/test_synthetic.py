"""가상 데이터는 dev·prd 에 넣지 않는다(§12.8, 00-common §3)."""

import pytest

from recommend.gate import is_forbidden_database


@pytest.mark.parametrize("name", ["dabaeum_dev", "dabaeum_prd", "DABAEUM_DEV", " dabaeum_prd "])
def test_dev_prd_DB_는_거부한다(name):
    assert is_forbidden_database(name)


@pytest.mark.parametrize("name", ["dabaeum_local", "dabaeum_synthetic", "postgres"])
def test_로컬_DB_는_허용한다(name):
    assert not is_forbidden_database(name)


def test_이름을_모르면_거부한다():
    assert is_forbidden_database(None)
    assert is_forbidden_database("")


def test_가상_회원은_지역_없이_관심_분야와_동의_기록을_갖는다():
    from datetime import datetime, timezone

    import numpy as np

    import synthetic

    rng = np.random.default_rng(7)
    users = synthetic._users(rng, datetime(2026, 11, 1, tzinfo=timezone.utc))
    interests, agreements = synthetic._user_interests(users, datetime(2026, 11, 1, tzinfo=timezone.utc))
    assert "region_code" not in users.columns
    assert all("region_code" not in columns for _, _, columns in synthetic.INSERT_ORDER)
    # 선택 입력이라 관심 분야가 없는 회원이 있고, 있는 회원은 1~2개다
    per_user = interests.groupby("user_id").size()
    assert 0 < len(per_user) < len(users) and per_user.max() <= 2
    assert interests["classification_code"].str.fullmatch(r"TSIX_0[1-7]0000").all()
    # 동의 기록은 관심 분야가 있는 회원에게만, 한 번씩
    assert set(agreements["user_id"]) == set(per_user.index) and agreements["user_id"].is_unique
    assert set(agreements["agreement_type"]) == {"INTEREST"} and agreements["agreed"].all()


def test_관심_분야_대분류_강좌의_선택_가중치가_더_크다():
    import numpy as np
    import pandas as pd

    import synthetic

    n = 600
    users = pd.DataFrame({"age": [30] * n, "gender": ["MALE"] * n,
                          "interest_majors": [("TSIX_04",)] * (n // 2) + [()] * (n // 2)})
    courses = pd.DataFrame({"classification_code": ["TSIX_040101", "TSIX_050101"],
                            "tuition_type": ["FREE", "FREE"]})
    weight = synthetic.affinity(users, courses, np.random.default_rng(1))
    ratio = weight[:, 0] / weight[:, 1]
    # 선호 대분류(무작위) 영향은 두 집단에 같게 섞이므로 평균 비율은 관심 분야 가중치만큼 벌어진다
    assert ratio[: n // 2].mean() > ratio[n // 2:].mean() * 2
