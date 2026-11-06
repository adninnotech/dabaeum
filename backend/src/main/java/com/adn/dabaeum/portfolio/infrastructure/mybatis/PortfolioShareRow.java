package com.adn.dabaeum.portfolio.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;

/** uuid[] 는 쉼표로 이은 문자열로 읽는다(생성자 매핑에서 배열 처리를 피한다). */
public record PortfolioShareRow(UUID id, UUID userId, String credentialIdsCsv, String badgeIdsCsv, Instant expiresAt,
    Instant revokedAt, int viewCount, Instant lastViewedAt, Instant createdAt) {
}
