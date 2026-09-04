package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CredentialStatusListMapper {

    int insert(CredentialStatusListRow row);

    CredentialStatusListRow selectById(@Param("listId") UUID listId);

    CredentialStatusListRow selectLatestByInstitutionId(@Param("institutionId") UUID institutionId);

    long countEntries(@Param("listId") UUID listId);

    int assignEntry(
        @Param("credentialId") UUID credentialId,
        @Param("listId") UUID listId,
        @Param("index") int index
    );

    CredentialStatusListEntryRow selectEntryByCredentialId(@Param("credentialId") UUID credentialId);

    List<Integer> selectRevokedIndexes(@Param("listId") UUID listId);
}
