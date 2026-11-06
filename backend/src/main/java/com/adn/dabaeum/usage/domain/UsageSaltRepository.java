package com.adn.dabaeum.usage.domain;

import java.time.LocalDate;

/** 이용 통계 전용 일별 salt (docs/specs/usage-statistics.md §3.1). 그날만 쓰고 다음 날 지운다. */
public interface UsageSaltRepository {

    /** 그날 salt. 없으면 만들어 넣고(서버 여러 대가 동시에 와도 한 행) 저장된 값을 돌려준다. */
    byte[] saltFor(LocalDate date);

    int deleteSaltsBefore(LocalDate date);
}
