package com.adn.dabaeum.credential.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KmsApprovalMapper {

    void insert(KmsApprovalRow row);

    KmsApprovalRow selectById(@Param("id") UUID id);

    List<KmsApprovalRow> selectOpenByInstitutionId(@Param("institutionId") UUID institutionId,
        @Param("now") Instant now);

    int consume(
        @Param("approvalId") UUID approvalId,
        @Param("institutionId") UUID institutionId,
        @Param("purpose") String purpose,
        @Param("action") String action,
        @Param("requester") UUID requester,
        @Param("signKeyId") UUID signKeyId,
        @Param("currentVersion") int currentVersion,
        @Param("targetKeyVersion") Integer targetKeyVersion,
        @Param("targetCompromisedAt") Instant targetCompromisedAt,
        @Param("paramsHash") String paramsHash,
        @Param("operationId") UUID operationId,
        @Param("now") Instant now
    );
}
