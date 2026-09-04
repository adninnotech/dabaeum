package com.adn.dabaeum.notification.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NotificationMapper {

    int insert(NotificationRow row);

    NotificationRow selectById(@Param("id") UUID id);

    List<NotificationRow> selectByUserId(
        @Param("userId") UUID userId,
        @Param("unreadOnly") boolean unreadOnly,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countByUserId(
        @Param("userId") UUID userId,
        @Param("unreadOnly") boolean unreadOnly
    );

    long countUnread(@Param("userId") UUID userId);

    int markRead(
        @Param("id") UUID id,
        @Param("userId") UUID userId,
        @Param("readAt") Instant readAt
    );
}
