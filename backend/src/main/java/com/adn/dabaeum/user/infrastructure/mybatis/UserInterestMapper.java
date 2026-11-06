package com.adn.dabaeum.user.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserInterestMapper {

    List<String> selectCodes(@Param("userId") UUID userId);

    int deleteByUserId(@Param("userId") UUID userId);

    int insert(@Param("userId") UUID userId, @Param("code") String code, @Param("createdAt") Instant createdAt);
}
