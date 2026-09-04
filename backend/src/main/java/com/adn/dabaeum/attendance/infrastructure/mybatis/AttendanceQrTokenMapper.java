package com.adn.dabaeum.attendance.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AttendanceQrTokenMapper {

    int insert(AttendanceQrTokenRow row);

    int revokeActiveBySession(
        @Param("sessionId") UUID sessionId,
        @Param("revokedAt") Instant revokedAt
    );

    AttendanceQrTokenRow selectByHash(@Param("tokenHash") String tokenHash);
}
