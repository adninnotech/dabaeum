package com.adn.dabaeum.blockchain.domain;

import java.util.List;

public interface BlockchainMonitoringRepository {

    BlockchainMetrics metrics();

    List<BlockchainTransactionSummary> findTransactions(int limit, int offset);

    long countTransactions();

    List<BlockchainAlert> findAlerts(int limit, int offset);

    long countAlerts();
}
