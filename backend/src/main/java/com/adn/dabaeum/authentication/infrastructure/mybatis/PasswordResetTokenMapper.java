package com.adn.dabaeum.authentication.infrastructure.mybatis;

import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PasswordResetTokenMapper {

    int insert(PasswordResetTokenRow row);

    PasswordResetTokenRow selectByTokenHash(@Param("tokenHash") String tokenHash);

    int markUsed(
        @Param("id") UUID id,
        @Param("usedAt") Instant usedAt
    );
}
