package com.adn.dabaeum.popularity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 설계서 §8.2 — score = 신청 × enrollmentWeight + 조회 × viewWeight, 최근 windowDays 일. */
@ConfigurationProperties(prefix = "dabaeum.popularity")
public record PopularityProperties(Integer windowDays, Integer enrollmentWeight, Integer viewWeight) {

    public PopularityProperties {
        windowDays = windowDays == null ? 30 : windowDays;
        enrollmentWeight = enrollmentWeight == null ? 3 : enrollmentWeight;
        viewWeight = viewWeight == null ? 1 : viewWeight;
        if (windowDays < 1 || enrollmentWeight < 0 || viewWeight < 0) {
            throw new IllegalArgumentException("popularity settings must be positive");
        }
    }
}
