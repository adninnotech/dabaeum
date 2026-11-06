package com.adn.dabaeum.recommendation.domain;

/** 항목 하나가 왜 추천됐는지 (설계 §6). 프론트가 문장으로 바꾼다. */
public enum RecommendationReasonType {
    MODEL_SCORE,
    SIMILAR_CLASSIFICATION,
    INTEREST,
    POPULAR_AGE_BAND,
    POPULAR
}
