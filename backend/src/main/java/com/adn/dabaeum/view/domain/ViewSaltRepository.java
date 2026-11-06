package com.adn.dabaeum.view.domain;

import java.time.LocalDate;

public interface ViewSaltRepository {

    /** 그날 salt. 없으면 만들어 넣고(서버 여러 대가 동시에 와도 한 행) 저장된 값을 돌려준다. */
    byte[] saltFor(LocalDate date);

    int deleteSaltsBefore(LocalDate date);
}
