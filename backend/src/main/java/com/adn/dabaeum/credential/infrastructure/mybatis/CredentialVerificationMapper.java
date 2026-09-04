package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CredentialVerificationMapper {

    int insert(CredentialVerificationRow row);

    List<CredentialVerificationRow> selectByCredentialId(
        @Param("credentialId") UUID credentialId,
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("sortField") String sortField,
        @Param("sortDirection") String sortDirection
    );

    long countByCredentialId(@Param("credentialId") UUID credentialId);
}
