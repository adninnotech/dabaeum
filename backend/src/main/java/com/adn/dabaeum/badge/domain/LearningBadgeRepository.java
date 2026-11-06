package com.adn.dabaeum.badge.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningBadgeRepository {

    void save(LearningBadge badge);

    Optional<LearningBadge> findById(UUID id);

    Optional<LearningBadge> findByCredentialIdAndTypeAndName(
        UUID credentialId, String badgeType, String badgeName);

    List<LearningBadge> findByUserId(UUID userId, int limit, int offset, String sort);

    long countByUserId(UUID userId);

    /** 정의 배지는 사람당 정의당 1회라 이미 받았는지 본다. */
    Optional<LearningBadge> findByUserIdAndDefinitionId(UUID userId, UUID definitionId);

    /** 관리자 배지 현황(설계서 §11.4). 발급 최신순. */
    List<LearningBadge> search(BadgeSearch criteria);

    long count(BadgeSearch criteria);

    /** keyword 는 KeywordQuery 로 정규화된 값: 이름 부분일치. */
    record BadgeSearch(UUID definitionId, LearningBadgeStatus status, Integer tierLevel, String keyword,
        int limit, int offset) {
    }
}
