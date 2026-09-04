package com.adn.dabaeum.fabric.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockchainTransactionRepository {
    List<BlockchainTransaction> claimDue(List<String> networks, int limit, Instant now);
    List<BlockchainTransaction> claimStale(
        List<String> networks,
        int limit,
        Instant now,
        Instant staleBefore
    );
    Optional<BlockchainTransaction> findById(UUID transactionId);
    Optional<BlockchainTransaction> findByIdempotencyKey(String network, String key);
    void insert(BlockchainTransaction transaction);
    void update(BlockchainTransaction transaction);
}
