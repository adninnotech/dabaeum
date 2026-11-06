package com.adn.dabaeum.dashboard.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardMapper {

    PlatformCountsRow selectPlatformCounts(@Param("now") Instant now);

    List<RecentMemberRow> selectRecentMembers(@Param("limit") int limit);

    InstitutionCountsRow selectInstitutionCounts(
        @Param("institutionId") UUID institutionId);

    List<CategoryCountRow> selectInstitutionCategoryCounts(
        @Param("institutionId") UUID institutionId);
}
