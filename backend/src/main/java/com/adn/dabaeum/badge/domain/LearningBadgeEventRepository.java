package com.adn.dabaeum.badge.domain;

import java.util.List;
import java.util.UUID;

/** 배지 변동 이력 저장소 (설계서 §11.4). 추가와 조회만 있다. */
public interface LearningBadgeEventRepository {

    void save(LearningBadgeEvent event);

    /** 최근 것부터. */
    List<LearningBadgeEvent> findByBadgeId(UUID badgeId);
}
