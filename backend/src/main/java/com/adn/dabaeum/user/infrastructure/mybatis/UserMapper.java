package com.adn.dabaeum.user.infrastructure.mybatis;

import com.adn.dabaeum.user.domain.UserPageCriteria;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    int insert(UserRow row);

    UserRow selectById(@Param("id") UUID id);

    List<UserRow> selectPage(@Param("criteria") UserPageCriteria criteria);

    long count(@Param("status") UserStatus status);

    int updateProfile(UserRow row);

    int updateStatus(
        @Param("id") UUID id,
        @Param("status") UserStatus status,
        @Param("withdrawnAt") Instant withdrawnAt,
        @Param("updatedAt") Instant updatedAt
    );

    Instant selectAuthInvalidatedAt(@Param("id") UUID id);

    int updateAuthInvalidatedAt(@Param("id") UUID id, @Param("at") Instant at);
}
