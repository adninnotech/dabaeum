package com.adn.dabaeum.portfolio.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 포트폴리오 읽기 모델 (설계서 §6). 새 데이터가 아니라 기존 수료·수료증·배지를 모은 것이다. */
public final class PortfolioRecords {

    private PortfolioRecords() {
    }

    public record Owner(String name, Instant joinedAt) {
    }

    public record CompletionItem(UUID completionId, UUID courseId, String title, String institutionName,
        Instant completedAt, String classificationCode, UUID credentialId) {
    }

    public record CredentialItem(UUID id, String credentialNo, String courseTitle, String institutionName,
        Instant completedAt, Instant issuedAt, String status) {
    }

    /** imageFileId 는 단계 배지면 현재 단계 이미지, 없으면 정의 이미지. */
    public record BadgeItem(UUID id, UUID definitionId, String definitionCode, String name, UUID imageFileId,
        Instant issuedAt, Integer tierLevel, String tierName) {
    }

    public record Share(UUID id, UUID userId, List<UUID> credentialIds, List<UUID> badgeIds, Instant expiresAt,
        Instant revokedAt, int viewCount, Instant lastViewedAt, Instant createdAt) {

        public Share {
            credentialIds = List.copyOf(credentialIds);
            badgeIds = List.copyOf(badgeIds);
        }
    }
}
