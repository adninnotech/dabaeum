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
}
