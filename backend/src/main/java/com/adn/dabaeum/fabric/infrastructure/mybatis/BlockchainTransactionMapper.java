package com.adn.dabaeum.fabric.infrastructure.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BlockchainTransactionMapper {
    List<BlockchainTransactionRow> claimDue(
        @Param("networks") List<String> networks,
        @Param("limit") int limit,
        @Param("now") Instant now
    );
    List<BlockchainTransactionRow> claimStale(
        @Param("networks") List<String> networks,
        @Param("limit") int limit,
        @Param("now") Instant now,
        @Param("staleBefore") Instant staleBefore);
    BlockchainTransactionRow selectById(@Param("transactionId") UUID transactionId);
    BlockchainTransactionRow selectByNetworkAndIdempotencyKey(
        @Param("network") String network, @Param("idempotencyKey") String idempotencyKey);
    int insert(BlockchainTransactionRow row);
    int update(BlockchainTransactionRow row);
}
