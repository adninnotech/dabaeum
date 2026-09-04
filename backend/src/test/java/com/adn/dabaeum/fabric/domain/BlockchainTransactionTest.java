package com.adn.dabaeum.fabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlockchainTransactionTest {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    @Test
    void claimsDuePendingTransactionAndConfirmsIt() {
        BlockchainTransaction transaction = pending();

        BlockchainTransaction processing = transaction.claim(NOW);
        BlockchainTransaction confirmed = processing.confirm("tx-001", "VALID", NOW.plusSeconds(2));

        assertThat(processing.status()).isEqualTo(BlockchainTransactionStatus.PROCESSING);
        assertThat(confirmed.status()).isEqualTo(BlockchainTransactionStatus.CONFIRMED);
        assertThat(confirmed.transactionId()).isEqualTo("tx-001");
        assertThat(confirmed.confirmedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void retryReturnsToPendingAndFailedIsTerminal() {
        BlockchainTransaction processing = pending().claim(NOW);

        BlockchainTransaction retry = processing.retry("FABRIC_SUBMIT_FAILED", "temporary",
            NOW.plusSeconds(30));
        assertThat(retry.status()).isEqualTo(BlockchainTransactionStatus.PENDING);
        assertThat(retry.retryCount()).isEqualTo(1);

        BlockchainTransaction failed = retry.claim(NOW.plusSeconds(30))
            .fail("FABRIC_COMMIT_INVALID", "invalid", NOW.plusSeconds(31));
        assertThat(failed.status()).isEqualTo(BlockchainTransactionStatus.FAILED);
        assertThatThrownBy(() -> failed.claim(NOW.plusSeconds(60)))
            .isInstanceOf(IllegalStateException.class);
    }

    private BlockchainTransaction pending() {
        UUID id = UUID.randomUUID();
        return new BlockchainTransaction(id, "CREDENTIAL", UUID.randomUUID(),
            "DABAEUM_FABRIC", BlockchainTransactionType.VC_ANCHOR, "task7-key", UUID.randomUUID(),
            "a".repeat(64), null, BlockchainTransactionStatus.PENDING, null, null,
            NOW, null, 0, null, null, NOW, NOW);
    }
}
