package com.adn.dabaeum.authentication.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LoginAttemptMapper {

    Instant selectLockedUntil(@Param("userId") UUID userId);

    Boolean recordFailure(
        @Param("userId") UUID userId,
        @Param("threshold") int threshold,
        @Param("lockUntil") Instant lockUntil
    );

    int clear(@Param("userId") UUID userId);
}
