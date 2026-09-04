package com.adn.dabaeum.blockchain.application;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.blockchain.domain.BlockchainAlert;
import com.adn.dabaeum.blockchain.domain.BlockchainMetrics;
import com.adn.dabaeum.blockchain.domain.BlockchainMonitoringRepository;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.BlockchainTransactionSummary;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "dev"})
public class DefaultBlockchainMonitoringService implements BlockchainMonitoringService {

    private final BlockchainMonitoringRepository repository;
    private final AuthorizationPolicy authorizationPolicy;
    private final BlockchainRegistryPort registry;

    public DefaultBlockchainMonitoringService(
        BlockchainMonitoringRepository repository,
        AuthorizationPolicy authorizationPolicy,
        BlockchainRegistryPort registry
    ) {
        this.repository = repository;
        this.authorizationPolicy = authorizationPolicy;
        this.registry = registry;
    }

    @Override
    @Transactional(readOnly = true)
    public BlockchainMetrics metrics(AuthenticatedUserContext actor) {
        authorizationPolicy.requirePlatformAdmin(actor);
        BlockchainMetrics metrics = repository.metrics();
        // 블록 높이는 DB 에 없고 원장 조회로만 알 수 있다. 조회가 안 되면 null 로 둔다.
        return registry.ledgerHeight().stream().boxed().findFirst()
            .map(height -> new BlockchainMetrics(height, metrics.transactionTotal(),
                metrics.transactionConfirmed(), metrics.transactionFailed(),
                metrics.successRate(), metrics.credentialIssuedCount()))
            .orElse(metrics);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BlockchainTransactionSummary> transactions(
        AuthenticatedUserContext actor, int page, int size
    ) {
        authorizationPolicy.requirePlatformAdmin(actor);
        int offset = Math.multiplyExact(page, size);
        var data = repository.findTransactions(size, offset);
        long totalElements = repository.countTransactions();
        return new Page<>(data, page, size, totalElements,
            totalPages(totalElements, size));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BlockchainAlert> alerts(
        AuthenticatedUserContext actor, int page, int size
    ) {
        authorizationPolicy.requirePlatformAdmin(actor);
        int offset = Math.multiplyExact(page, size);
        var data = repository.findAlerts(size, offset);
        long totalElements = repository.countAlerts();
        return new Page<>(data, page, size, totalElements,
            totalPages(totalElements, size));
    }

    private static int totalPages(long totalElements, int size) {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }
}
