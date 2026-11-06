package com.adn.dabaeum.statistics.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StatisticsRepository {

    /** [from, toExclusive). CONTENT 는 applied 내림차순 상위 limit, 나머지는 key 오름차순 전체. */
    List<EnrollmentStatRow> enrollments(EnrollmentStatGroup group, Instant from, Instant toExclusive,
        String classificationPrefix, UUID institutionId, int limit);

    List<BadgeStatRow> badges(BadgeStatGroup group, Instant from, Instant toExclusive);
}
