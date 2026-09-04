package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CredentialMapper {

    int insert(CredentialRow row);

    int insertIfChainKeyAvailable(CredentialRow row);

    CredentialRow selectById(@Param("credentialId") UUID credentialId);

    CredentialRow selectByCredentialNo(@Param("credentialNo") String credentialNo);

    CredentialRow selectByCredentialHash(@Param("credentialHash") String credentialHash);

    CredentialRow selectByIdForUpdate(@Param("credentialId") UUID credentialId);

    CredentialRow selectActiveByGroupId(@Param("groupId") UUID groupId);

    List<CredentialRow> selectByUserId(
        @Param("userId") UUID userId,
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("sortField") String sortField,
        @Param("sortDirection") String sortDirection
    );

    long countByUserId(@Param("userId") UUID userId);

    Integer selectLatestVersionForUpdate(@Param("groupId") UUID groupId);

    int update(CredentialRow row);
}
