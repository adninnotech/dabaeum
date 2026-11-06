package com.adn.dabaeum.user.infrastructure.mybatis;

import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.user.domain.UserListFilter;
import com.adn.dabaeum.user.domain.UserPageCriteria;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    int insert(UserRow row);

    UserRow selectById(@Param("id") UUID id);

    List<UserRow> selectActiveLearnersByPhone(@Param("phoneDigits") String phoneDigits, @Param("name") String name,
        @Param("birthDate") LocalDate birthDate, @Param("institutionId") UUID institutionId, @Param("limit") int limit);

    List<UserListRow> selectPage(@Param("criteria") UserPageCriteria criteria);

    long countFiltered(@Param("filter") UserListFilter filter);

    int updateProfile(UserRow row);

    int updateStatus(
        @Param("id") UUID id,
        @Param("status") UserStatus status,
        @Param("withdrawnAt") Instant withdrawnAt,
        @Param("updatedAt") Instant updatedAt
    );

    Instant selectAuthInvalidatedAt(@Param("id") UUID id);

    int updateAuthInvalidatedAt(@Param("id") UUID id, @Param("at") Instant at);

    int recordLastLogin(@Param("userId") UUID userId, @Param("at") Instant at);

    int updateNotificationPreferences(
        @Param("id") UUID id,
        @Param("emailNotificationEnabled") boolean emailNotificationEnabled,
        @Param("smsNotificationEnabled") boolean smsNotificationEnabled,
        @Param("updatedAt") Instant updatedAt
    );

    int updateAdminMemo(
        @Param("userId") UUID userId,
        @Param("memo") String memo,
        @Param("updatedAt") Instant updatedAt
    );
}
