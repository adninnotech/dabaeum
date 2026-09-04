package com.adn.dabaeum.fabric;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.fabric.domain.BlockchainTransaction;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionType;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BlockchainTransactionMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    @Autowired
    BlockchainTransactionRepository repository;

    /** 이 테스트의 픽스처가 사용하는 네트워크. */
    private static final java.util.List<String> LEGACY_NETWORKS =
        java.util.List.of("DABAEUM_FABRIC");

    @Test
    void claimsOnlyDueRowsAndPersistsPublicCommitMetadata() {
        BlockchainTransaction due = pending(null);
        BlockchainTransaction future = pending(NOW.plusSeconds(3600));
        repository.insert(due);
        repository.insert(future);

        var claimed = repository.claimDue(LEGACY_NETWORKS, 10, NOW);

        assertThat(claimed).extracting(BlockchainTransaction::id).containsExactly(due.id());
        assertThat(repository.findById(future.id())).get()
            .extracting(BlockchainTransaction::status)
            .isEqualTo(BlockchainTransactionStatus.PENDING);

        repository.update(claimed.get(0).confirm("tx-task7", "VALID", NOW.plusSeconds(1)));

        assertThat(repository.findById(due.id())).get()
            .extracting(BlockchainTransaction::responseMetadata)
            .satisfies(metadata -> assertThat(metadata.toString()).contains("\"commitCode\": \"VALID\""));
    }

    @Test
    void enforcesNetworkIdempotency() {
        BlockchainTransaction first = pending(null);
        repository.insert(first);
        assertSqlState("23505", () -> repository.insert(pendingWithKey(first.idempotencyKey())));
    }

    @Test
    void enforcesTransactionIdUniqueness() {
        BlockchainTransaction confirmed = pending(null).claim(NOW).confirm("tx-unique", "VALID", NOW);
        repository.insert(confirmed);
        BlockchainTransaction duplicateTransactionId = pending(null).claim(NOW)
            .confirm("tx-unique", "VALID", NOW);
        assertSqlState("23505", () -> repository.insert(duplicateTransactionId));
    }

    @Test
    void preservesOperationReasonAcrossClaimAndRetry() {
        BlockchainTransaction transaction = pendingWithReason("reissue corrected attendance");
        repository.insert(transaction);

        BlockchainTransaction claimed = repository.claimDue(LEGACY_NETWORKS, 10, NOW).get(0);
        assertThat(claimed.operationReason()).isEqualTo("reissue corrected attendance");

        repository.update(claimed.retry("FABRIC_READ_FAILED", "temporary", NOW.plusSeconds(30)));
        assertThat(repository.findById(transaction.id())).get()
            .extracting(BlockchainTransaction::operationReason)
            .isEqualTo("reissue corrected attendance");
    }

    private BlockchainTransaction pending(Instant nextRetryAt) {
        BlockchainTransaction transaction = new BlockchainTransaction(
            UUID.randomUUID(), "CREDENTIAL", UUID.randomUUID(), "DABAEUM_FABRIC",
            BlockchainTransactionType.VC_ANCHOR, "task7-" + UUID.randomUUID(), UUID.randomUUID(),
            "a".repeat(64), null, BlockchainTransactionStatus.PENDING,
            nextRetryAt == null ? null : "FABRIC_SUBMIT_FAILED", null, NOW, null,
            nextRetryAt == null ? 0 : 1, nextRetryAt, null, NOW, NOW);
        return transaction;
    }

    private BlockchainTransaction pendingWithKey(String key) {
        return new BlockchainTransaction(UUID.randomUUID(), "CREDENTIAL", UUID.randomUUID(),
            "DABAEUM_FABRIC", BlockchainTransactionType.VC_ANCHOR, key, UUID.randomUUID(),
            "b".repeat(64), null, BlockchainTransactionStatus.PENDING, null, null,
            NOW, null, 0, null, null, NOW, NOW);
    }

    private BlockchainTransaction pendingWithReason(String reason) {
        return new BlockchainTransaction(UUID.randomUUID(), "CREDENTIAL", UUID.randomUUID(),
            "DABAEUM_FABRIC", BlockchainTransactionType.VC_REISSUE,
            "reason-" + UUID.randomUUID(), UUID.randomUUID(), "c".repeat(64), null,
            BlockchainTransactionStatus.PENDING, null, null, NOW, null, 0, null, null, NOW, NOW,
            reason);
    }
}
