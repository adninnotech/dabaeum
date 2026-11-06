package com.adn.dabaeum.badge.infrastructure.mybatis;

import com.adn.dabaeum.badge.domain.LearningBadgeEvent;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LearningBadgeEventMapper {

    int insert(LearningBadgeEvent event);

    List<LearningBadgeEvent> selectByBadgeId(@Param("badgeId") UUID badgeId);
}
