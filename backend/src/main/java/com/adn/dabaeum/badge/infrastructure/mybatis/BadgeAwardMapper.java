package com.adn.dabaeum.badge.infrastructure.mybatis;

import com.adn.dabaeum.badge.domain.BadgeAwardRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BadgeAwardMapper {

    long countCompleted(@Param("userId") UUID userId, @Param("prefix") String prefix);

    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("userId") UUID userId,
        @Param("courseId") UUID courseId,
        @Param("definitionId") UUID definitionId,
        @Param("badgeType") String badgeType,
        @Param("badgeName") String badgeName,
        @Param("tierLevel") Integer tierLevel,
        @Param("at") Instant at
    );

    long countCompletedCourse(@Param("userId") UUID userId, @Param("courseId") UUID courseId);

    BadgeAwardRepository.AwardedBadge lockAwarded(@Param("userId") UUID userId, @Param("definitionId") UUID definitionId);

    List<BadgeAwardRepository.AwardedBadge> selectIssuedAutoBadges(@Param("userId") UUID userId);

    int revoke(@Param("id") UUID id, @Param("at") Instant at);

    int restore(@Param("id") UUID id, @Param("courseId") UUID courseId, @Param("tierLevel") Integer tierLevel,
        @Param("at") Instant at);

    int changeLevel(@Param("id") UUID id, @Param("tierLevel") int tierLevel, @Param("at") Instant at);
}
