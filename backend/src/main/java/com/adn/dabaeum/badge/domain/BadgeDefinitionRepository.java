package com.adn.dabaeum.badge.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BadgeDefinitionRepository {

    void save(BadgeDefinition definition);

    Optional<BadgeDefinition> findById(UUID id);

    /** 생성 순. activeOnly 면 활성만. */
    List<BadgeDefinition> findAll(boolean activeOnly);

    boolean update(BadgeDefinition definition);

    boolean delete(UUID id);

    /** 이 정의로 준 배지가 있는지. 있으면 규칙 변경·삭제를 막는다. */
    boolean hasIssuedBadges(UUID id);
}
