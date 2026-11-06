package com.adn.dabaeum.recommendation.domain;

/**
 * 추천 목록의 근거 (docs/specs/course-recommendation.md §2). 프론트가 섹션 제목과 배치를 정한다.
 * MODEL 은 야간 배치의 모델 점수(§12.5), PERSONAL 은 찜·신청 신호, INTEREST 는 회원이 고른 관심 분야, 나머지는 신호가 없을 때 대신 채운 순서다(§4).
 */
public enum RecommendationBasis {
    MODEL,
    PERSONAL,
    INTEREST,
    AGE_BAND,
    POPULAR
}
