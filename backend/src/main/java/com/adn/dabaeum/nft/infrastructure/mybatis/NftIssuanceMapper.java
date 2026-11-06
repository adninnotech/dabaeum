package com.adn.dabaeum.nft.infrastructure.mybatis;

import com.adn.dabaeum.nft.domain.NftIssuance;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NftIssuanceMapper {
    int insertIfAbsent(NftIssuance issuance);
    NftIssuance selectById(@Param("id") UUID id);
    List<NftIssuance> claimDue(@Param("limit") int limit, @Param("now") Instant now,
        @Param("badgeContract") String badgeContract, @Param("credentialContract") String credentialContract);
    List<NftIssuance> selectByStatus(@Param("status") String status, @Param("limit") int limit);
    List<NftIssuance> selectStaleSubmitting(@Param("before") Instant before, @Param("limit") int limit);
    int updateFrom(@Param("expected") String expected, @Param("next") NftIssuance next);
    int touch(@Param("id") UUID id, @Param("expected") String expected, @Param("now") Instant now);
    int markRevokedTargets(@Param("now") Instant now);
    int cancelRevokedBeforeSubmit(@Param("now") Instant now);
}
