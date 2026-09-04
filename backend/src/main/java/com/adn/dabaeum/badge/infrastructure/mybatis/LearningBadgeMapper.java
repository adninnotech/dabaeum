package com.adn.dabaeum.badge.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LearningBadgeMapper {

    int insert(LearningBadgeRow row);

    LearningBadgeRow selectById(@Param("id") UUID id);

    LearningBadgeRow selectByCredentialIdAndTypeAndName(
        @Param("credentialId") UUID credentialId,
        @Param("badgeType") String badgeType,
        @Param("badgeName") String badgeName
    );

    List<LearningBadgeRow> selectByUserId(
        @Param("userId") UUID userId,
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("sortField") String sortField,
        @Param("sortDirection") String sortDirection
    );

    long countByUserId(@Param("userId") UUID userId);
}
