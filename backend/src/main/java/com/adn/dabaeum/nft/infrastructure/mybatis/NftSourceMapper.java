package com.adn.dabaeum.nft.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NftSourceMapper {
    List<NftSourceRow> selectUnrecordedCredentials(@Param("since") Instant since, @Param("limit") int limit);
    List<NftSourceRow> selectUnrecordedBadges(@Param("since") Instant since, @Param("limit") int limit);
    NftSourceRow selectCredential(@Param("targetId") UUID targetId);
    NftSourceRow selectBadge(@Param("targetId") UUID targetId);
}
