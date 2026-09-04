package com.adn.dabaeum.authentication.infrastructure.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LocalAccountMapper {

    int insert(LocalAccountRow row);

    LocalAccountRow selectByNormalizedEmail(
        @Param("normalizedEmail") String normalizedEmail
    );

    LocalAccountRow selectByUserId(@Param("userId") java.util.UUID userId);

    int updatePasswordHash(
        @Param("userId") java.util.UUID userId,
        @Param("passwordHash") String passwordHash,
        @Param("updatedAt") java.time.Instant updatedAt
    );
}
