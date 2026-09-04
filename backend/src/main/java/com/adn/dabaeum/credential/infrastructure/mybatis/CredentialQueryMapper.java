package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CredentialQueryMapper {

    CredentialCourseViewRow selectCourseByCredentialId(
        @Param("credentialId") UUID credentialId);

    List<CredentialCourseViewRow> selectCoursesByCredentialIds(
        @Param("credentialIds") List<UUID> credentialIds);

    List<CredentialRow> selectByUserIdAndInstitutionIds(
        @Param("userId") UUID userId,
        @Param("institutionIds") List<UUID> institutionIds,
        @Param("limit") int limit,
        @Param("offset") int offset,
        @Param("sortField") String sortField,
        @Param("sortDirection") String sortDirection
    );

    long countByUserIdAndInstitutionIds(
        @Param("userId") UUID userId,
        @Param("institutionIds") List<UUID> institutionIds);
}
