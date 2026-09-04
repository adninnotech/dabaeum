package com.adn.dabaeum.blockchain.infrastructure.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BlockchainMonitoringMapper {

    BlockchainMetricsRow selectMetrics();

    List<BlockchainTransactionSummaryRow> selectTransactions(
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countTransactions();

    List<BlockchainAlertRow> selectAlerts(
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countAlerts();
}
