package com.adn.dabaeum.fabric.infrastructure.mybatis;

import com.adn.dabaeum.fabric.domain.BlockchainTransaction;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "dev"})
public class BlockchainTransactionMyBatisRepository implements BlockchainTransactionRepository {
    private final BlockchainTransactionMapper mapper;
    public BlockchainTransactionMyBatisRepository(BlockchainTransactionMapper mapper) { this.mapper = mapper; }
    @Override public List<BlockchainTransaction> claimDue(
        List<String> networks,
        int limit,
        Instant now
    ) {
        validateNetworks(networks);
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        return mapper.claimDue(networks, limit, now).stream().map(this::toDomain).toList();
    }
    @Override public List<BlockchainTransaction> claimStale(
        List<String> networks,
        int limit,
        Instant now,
        Instant staleBefore
    ) {
        validateNetworks(networks);
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        return mapper.claimStale(networks, limit, now, staleBefore).stream()
            .map(this::toDomain).toList();
    }
    @Override public Optional<BlockchainTransaction> findById(UUID id) { return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain); }
    @Override public Optional<BlockchainTransaction> findByIdempotencyKey(String network, String key) { return Optional.ofNullable(mapper.selectByNetworkAndIdempotencyKey(network, key)).map(this::toDomain); }
    @Override public void insert(BlockchainTransaction transaction) { mapper.insert(toRow(transaction)); }
    @Override public void update(BlockchainTransaction transaction) { mapper.update(toRow(transaction)); }
    /** IN 절에 들어가는 값이므로 알려진 network 이름만 허용한다. 새 Provider 를 붙이면 여기도 늘린다. */
    private static final java.util.Set<String> KNOWN_NETWORKS = java.util.Set.of(
        "DABAEUM_FABRIC", "FABRIC_POC", "DAEGUCHAIN");

    private void validateNetworks(List<String> networks) {
        if (networks == null || networks.isEmpty()
            || networks.stream().anyMatch(network -> !KNOWN_NETWORKS.contains(network))) {
            throw new IllegalArgumentException("networks must contain only supported values");
        }
    }
    private BlockchainTransaction toDomain(BlockchainTransactionRow r) {
        return new BlockchainTransaction(r.id(), r.referenceType(), r.referenceId(), r.network(),
            BlockchainTransactionType.valueOf(r.transactionType()), r.idempotencyKey(), r.correlationId(),
            r.requestHash(), r.transactionId(), BlockchainTransactionStatus.valueOf(r.status()),
            r.errorCode(), r.errorMessage(), r.requestedAt(), r.confirmedAt(), r.retryCount(),
            r.nextRetryAt(), r.responseMetadata(), r.createdAt(), r.updatedAt(), r.operationReason());
    }
    private BlockchainTransactionRow toRow(BlockchainTransaction t) {
        return new BlockchainTransactionRow(t.id(), t.referenceType(), t.referenceId(), t.network(),
            t.transactionType().name(), t.idempotencyKey(), t.correlationId(), t.requestHash(),
            t.transactionId(), t.status().name(), t.errorCode(), t.errorMessage(), t.requestedAt(),
            t.confirmedAt(), t.retryCount(), t.nextRetryAt(), t.responseMetadata(), t.createdAt(), t.updatedAt(),
            t.operationReason());
    }
}
