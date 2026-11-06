package com.adn.dabaeum.statistics.infrastructure.mybatis;

import com.adn.dabaeum.statistics.domain.BadgeStatGroup;
import com.adn.dabaeum.statistics.domain.BadgeStatRow;
import com.adn.dabaeum.statistics.domain.EnrollmentStatGroup;
import com.adn.dabaeum.statistics.domain.EnrollmentStatRow;
import com.adn.dabaeum.statistics.domain.StatisticsRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class StatisticsMyBatisRepository implements StatisticsRepository {

    private final StatisticsMapper mapper;

    public StatisticsMyBatisRepository(StatisticsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<EnrollmentStatRow> enrollments(EnrollmentStatGroup group, Instant from, Instant toExclusive,
        String classificationPrefix, UUID institutionId, int limit) {
        return mapper.selectEnrollments(group.name(), from, toExclusive, classificationPrefix, institutionId, limit);
    }

    @Override
    public List<BadgeStatRow> badges(BadgeStatGroup group, Instant from, Instant toExclusive) {
        return mapper.selectBadges(group.name(), from, toExclusive);
    }
}
