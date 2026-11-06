package com.adn.dabaeum.recommendation.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 추천 설정 (docs/specs/course-recommendation.md §5). 값은 모두 경험칙이다(§8) — 근거 데이터가 쌓이면 바꾼다.
 *
 * @param defaultLimit limit 을 안 주면 몇 건
 * @param maxLimit limit 상한
 * @param maxExclude exclude 로 받는 강좌 id 상한
 * @param freshDays 등록 후 이 일수 안이면 신규로 본다
 * @param freshBonus 신규 강좌에 더하는 인기 점수
 */
@ConfigurationProperties(prefix = "dabaeum.recommendation")
public record RecommendationProperties(
    Integer defaultLimit, Integer maxLimit, Integer maxExclude, Integer freshDays, Integer freshBonus
) {

    public RecommendationProperties {
        defaultLimit = defaultLimit == null ? 6 : defaultLimit;
        maxLimit = maxLimit == null ? 20 : maxLimit;
        maxExclude = maxExclude == null ? 20 : maxExclude;
        freshDays = freshDays == null ? 7 : freshDays;
        freshBonus = freshBonus == null ? 5 : freshBonus;
        if (defaultLimit < 1 || maxLimit < defaultLimit || maxExclude < 0 || freshDays < 0 || freshBonus < 0) {
            throw new IllegalArgumentException("recommendation settings out of range");
        }
    }
}
