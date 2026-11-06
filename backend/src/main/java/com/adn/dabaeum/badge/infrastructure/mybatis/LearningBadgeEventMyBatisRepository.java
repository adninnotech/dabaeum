package com.adn.dabaeum.badge.infrastructure.mybatis;

import com.adn.dabaeum.badge.domain.LearningBadgeEvent;
import com.adn.dabaeum.badge.domain.LearningBadgeEventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class LearningBadgeEventMyBatisRepository implements LearningBadgeEventRepository {

    private final LearningBadgeEventMapper mapper;

    public LearningBadgeEventMyBatisRepository(LearningBadgeEventMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(LearningBadgeEvent event) {
        mapper.insert(event);
    }

    @Override
    public List<LearningBadgeEvent> findByBadgeId(UUID badgeId) {
        return mapper.selectByBadgeId(badgeId);
    }
}
