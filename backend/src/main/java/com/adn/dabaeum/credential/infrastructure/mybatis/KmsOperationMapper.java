package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KmsOperationMapper {

    void insert(KmsOperationRow row);

    KmsOperationRow selectById(@Param("id") UUID id);

    KmsOperationRow selectInProgressBySignKeyId(@Param("signKeyId") UUID signKeyId);

    List<KmsOperationRow> selectByInstitutionId(@Param("institutionId") UUID institutionId,
        @Param("limit") int limit);

    List<KmsOperationRow> selectInProgress(@Param("limit") int limit);

    int acquireOwnership(@Param("id") UUID id, @Param("owner") String owner, @Param("now") Instant now,
        @Param("leaseUntil") Instant leaseUntil);

    int reconfirmOwnership(@Param("id") UUID id, @Param("owner") String owner, @Param("status") String status,
        @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    int update(KmsOperationRow row);

    int releaseOwnership(@Param("id") UUID id, @Param("owner") String owner);
}
