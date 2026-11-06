package com.adn.dabaeum.statistics.infrastructure.mybatis;

import com.adn.dabaeum.statistics.domain.BadgeStatRow;
import com.adn.dabaeum.statistics.domain.EnrollmentStatRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StatisticsMapper {

    List<EnrollmentStatRow> selectEnrollments(
        @Param("group") String group,
        @Param("from") Instant from,
        @Param("to") Instant toExclusive,
        @Param("prefix") String classificationPrefix,
        @Param("institutionId") UUID institutionId,
        @Param("limit") int limit
    );

    List<BadgeStatRow> selectBadges(
        @Param("group") String group,
        @Param("from") Instant from,
        @Param("to") Instant toExclusive
    );
}
