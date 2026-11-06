package com.adn.dabaeum.banner.domain;

/**
 * 배너 노출 방식.
 *
 * <p>기본값이 {@link #PRIVATE} 인 이유: 임시저장으로 만든 배너가 실수로 홈 최상단에 뜨면
 * 안 된다. 공개는 언제나 명시적이어야 한다.
 */
public enum BannerVisibility {
    PUBLIC,
    SCHEDULED,
    PRIVATE
}
