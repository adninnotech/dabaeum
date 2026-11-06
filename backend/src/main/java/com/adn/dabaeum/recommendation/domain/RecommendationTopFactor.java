package com.adn.dabaeum.recommendation.domain;

/** 모델 점수에 가장 크게 기여한 속성 묶음 (설계 §12.5). 프론트가 이유 문장의 꼬리말을 고른다. */
public enum RecommendationTopFactor {
    CLASSIFICATION,
    INTEREST,
    AGE_BAND,
    PRICE,
    SCHEDULE,
    POPULARITY
}
