package com.adn.dabaeum.fabric.infrastructure.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.UUID;

@Mapper
public interface PendingBlockchainRequestMapper {

    PendingBlockchainRequestRow selectByNetworkAndIdempotencyKey(
        @Param("network") String network,
        @Param("idempotencyKey") String idempotencyKey
    );

    int insert(PendingBlockchainRequestRow row);

    boolean existsPendingOperationForGroup(@Param("credentialGroupId") UUID credentialGroupId);
}
