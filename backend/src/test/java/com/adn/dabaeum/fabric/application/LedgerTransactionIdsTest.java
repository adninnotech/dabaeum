package com.adn.dabaeum.fabric.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.blockchain.domain.BlockchainHistoryEntry;
import com.adn.dabaeum.blockchain.domain.BlockchainHistoryQuery;
import com.adn.dabaeum.blockchain.domain.BlockchainProvider;
import com.adn.dabaeum.blockchain.domain.BlockchainReceipt;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryCreate;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReissue;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryState;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryUpdate;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerTransactionIdsTest {

    /**
     * 2026-09-11 테스트베드 실측: 블록 18(폐기)의 timestamp 가 블록 17(발급)보다 3분 이르다.
     * 가장 최근 트랜잭션은 시각이 아니라 Port 가 돌려주는 원장 순서(마지막 항목)로 고른다.
     */
    @Test
    void latestFollowsLedgerOrderNotTheSkewedTimestamp() {
        CredentialRegistryReference reference =
            new CredentialRegistryReference(BlockchainProvider.DAEGUCHAIN, "TB1DFHZXO2T2DZKR", null);
        BlockchainRegistryPort registry = new HistoryOnlyRegistry(List.of(
            entry("HuWiX45-block17", RegistryStatus.ACTIVE, "2026-09-11T01:48:06Z"),
            entry("3agxJFm-block18", RegistryStatus.REVOKED, "2026-09-11T01:45:14Z")));

        assertThat(LedgerTransactionIds.latest(registry, reference)).hasValue("3agxJFm-block18");
    }

    private static BlockchainHistoryEntry entry(String transactionId, RegistryStatus status, String timestamp) {
        CredentialRegistryState state = new CredentialRegistryState("DCSTORE:TB1DFHZXO2T2DZKR", 1, status,
            "b".repeat(64), Instant.parse(timestamp), BlockchainProvider.DAEGUCHAIN);
        return new BlockchainHistoryEntry(transactionId, state, Instant.parse(timestamp), false);
    }

    private record HistoryOnlyRegistry(List<BlockchainHistoryEntry> oldestFirst) implements BlockchainRegistryPort {
        @Override public List<BlockchainHistoryEntry> getCredentialHistory(
            CredentialRegistryReference reference, BlockchainHistoryQuery query) {
            return oldestFirst;
        }
        @Override public BlockchainReceipt createCredentialState(CredentialRegistryCreate command) {
            throw new UnsupportedOperationException();
        }
        @Override public CredentialRegistryState getCredentialState(CredentialRegistryReference reference) {
            throw new UnsupportedOperationException();
        }
        @Override public BlockchainReceipt updateCredentialState(CredentialRegistryUpdate command) {
            throw new UnsupportedOperationException();
        }
        @Override public BlockchainReceipt reissueCredentialState(CredentialRegistryReissue command) {
            throw new UnsupportedOperationException();
        }
    }
}
