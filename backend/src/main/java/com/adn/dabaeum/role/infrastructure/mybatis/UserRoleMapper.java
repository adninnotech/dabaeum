package com.adn.dabaeum.role.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserRoleMapper {

    int insert(UserRoleRow row);

    int insertIfAbsent(UserRoleRow row);

    UserRoleRow selectById(@Param("id") UUID id);

    UserRoleRow selectByIdForUpdate(@Param("id") UUID id);

    List<UserRoleRow> selectByUserId(@Param("userId") UUID userId);

    List<UserRoleRow> selectByUserIdAndInstitution(
        @Param("userId") UUID userId,
        @Param("institutionId") UUID institutionId
    );

    List<UserRoleRow> selectByUserIdAndInstitutionForUpdate(
        @Param("userId") UUID userId,
        @Param("institutionId") UUID institutionId
    );

    int deleteByIdAndUserId(
        @Param("roleId") UUID roleId,
        @Param("userId") UUID userId
    );

    int updateInstructorMemo(
        @Param("institutionId") UUID institutionId,
        @Param("userId") UUID userId,
        @Param("memo") String memo
    );
}
