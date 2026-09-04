package com.adn.dabaeum.blockchain.infrastructure.mybatis;

import com.adn.dabaeum.blockchain.domain.BlockchainAlert;
import com.adn.dabaeum.blockchain.domain.BlockchainMetrics;
import com.adn.dabaeum.blockchain.domain.BlockchainMonitoringRepository;
import com.adn.dabaeum.blockchain.domain.BlockchainTransactionSummary;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class BlockchainMonitoringMyBatisRepository
    implements BlockchainMonitoringRepository {

    private final BlockchainMonitoringMapper mapper;

    public BlockchainMonitoringMyBatisRepository(BlockchainMonitoringMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public BlockchainMetrics metrics() {
        BlockchainMetricsRow row = mapper.selectMetrics();
        long total = z(row == null ? null : row.transactionTotal());
        long confirmed = z(row == null ? null : row.transactionConfirmed());
        long failed = z(row == null ? null : row.transactionFailed());
        long decided = confirmed + failed;
        Double successRate = decided == 0
            ? null : Math.round(confirmed * 10000.0 / decided) / 100.0;
        // 블록 높이는 원장 조회가 필요하므로 DB 기반 모니터링에서는 제공하지 않는다.
        return new BlockchainMetrics(
            null, total, confirmed, failed, successRate,
            z(row == null ? null : row.credentialIssuedCount()));
    }

    @Override
    public List<BlockchainTransactionSummary> findTransactions(int limit, int offset) {
        return mapper.selectTransactions(limit, offset).stream()
            .map(row -> new BlockchainTransactionSummary(
                row.id(), row.txHash(), row.type(), row.status(), row.occurredAt(),
                row.requestedAt(), row.confirmedAt(), row.blockNumber(),
                Boolean.TRUE.equals(row.reconciled())))
            .toList();
    }

    @Override
    public long countTransactions() {
        return mapper.countTransactions();
    }

    @Override
    public List<BlockchainAlert> findAlerts(int limit, int offset) {
        return mapper.selectAlerts(limit, offset).stream()
            .map(row -> new BlockchainAlert(
                row.id(), row.severity(), row.message(), row.occurredAt()))
            .toList();
    }

    @Override
    public long countAlerts() {
        return mapper.countAlerts();
    }

    private static long z(Long value) {
        return value == null ? 0 : value;
    }
}
