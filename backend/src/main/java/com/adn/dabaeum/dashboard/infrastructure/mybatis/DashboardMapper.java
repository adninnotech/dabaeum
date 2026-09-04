package com.adn.dabaeum.dashboard.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DashboardMapper {

    PlatformCountsRow selectPlatformCounts();

    List<RecentMemberRow> selectRecentMembers(@Param("limit") int limit);

    InstitutionCountsRow selectInstitutionCounts(
        @Param("institutionId") UUID institutionId);
}
