package com.adn.dabaeum.badge.infrastructure.mybatis;

import com.adn.dabaeum.badge.domain.BadgeAwardRepository;
import com.adn.dabaeum.badge.domain.BadgeDefinition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class BadgeAwardMyBatisRepository implements BadgeAwardRepository {

    private final BadgeAwardMapper mapper;

    public BadgeAwardMyBatisRepository(BadgeAwardMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long countCompletedCourses(UUID userId, String classificationPrefix) {
        return mapper.countCompleted(userId, classificationPrefix);
    }

    @Override
    public long countCompletedCourse(UUID userId, UUID courseId) {
        return mapper.countCompletedCourse(userId, courseId);
    }

    @Override
    public Optional<AwardedBadge> lockAwarded(UUID userId, UUID definitionId) {
        return Optional.ofNullable(mapper.lockAwarded(userId, definitionId));
    }

    @Override
    public boolean insertIfAbsent(UUID badgeId, UUID userId, UUID courseId, BadgeDefinition definition,
        Integer tierLevel, Instant at) {
        return mapper.insertIfAbsent(badgeId, userId, courseId, definition.id(),
            definition.rule().type().name(), definition.name(), tierLevel, at) == 1;
    }

    @Override
    public boolean restore(UUID badgeId, UUID courseId, Integer tierLevel, Instant at) {
        return mapper.restore(badgeId, courseId, tierLevel, at) == 1;
    }

    @Override
    public boolean changeLevel(UUID badgeId, int tierLevel, Instant at) {
        return mapper.changeLevel(badgeId, tierLevel, at) == 1;
    }

    @Override
    public List<AwardedBadge> findIssuedAutoBadges(UUID userId) {
        return mapper.selectIssuedAutoBadges(userId);
    }

    @Override
    public boolean revoke(UUID badgeId, Instant at) {
        return mapper.revoke(badgeId, at) == 1;
    }
}
