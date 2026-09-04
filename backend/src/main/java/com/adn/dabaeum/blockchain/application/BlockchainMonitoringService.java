package com.adn.dabaeum.blockchain.application;

import com.adn.dabaeum.blockchain.domain.BlockchainAlert;
import com.adn.dabaeum.blockchain.domain.BlockchainMetrics;
import com.adn.dabaeum.blockchain.domain.BlockchainTransactionSummary;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.List;

public interface BlockchainMonitoringService {

    BlockchainMetrics metrics(AuthenticatedUserContext actor);

    Page<BlockchainTransactionSummary> transactions(
        AuthenticatedUserContext actor, int page, int size);

    Page<BlockchainAlert> alerts(AuthenticatedUserContext actor, int page, int size);

    record Page<T>(
        List<T> data, int page, int size, long totalElements, int totalPages) {
    }
}
