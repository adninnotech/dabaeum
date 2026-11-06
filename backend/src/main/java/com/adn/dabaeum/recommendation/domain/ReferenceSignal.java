package com.adn.dabaeum.recommendation.domain;

/** 관심 신호의 출처 (설계 §3.1). 찜과 수강 신청만 쓴다. 상세 조회·검색어는 쓰지 않는다. */
public enum ReferenceSignal {
    INTEREST,
    ENROLLMENT
}
