package com.adn.dabaeum.fabric.application;

import com.adn.dabaeum.blockchain.domain.BlockchainHistoryEntry;
import com.adn.dabaeum.blockchain.domain.BlockchainHistoryQuery;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DB 와 원장을 재조정할 때 쓰는 트랜잭션 ID 규칙.
 *
 * <p>원장에 이미 반영된 건은 이력에서 실제 트랜잭션 ID 를 찾아 기록한다. 이력을 읽지 못하면
 * 합성 ID 를 쓰되 접두사와 commitCode 로 구분해, 모니터링과 증빙에서 실제 ID 로 오인하지
 * 않게 한다.
 */
final class LedgerTransactionIds {

    static final String RECONCILED_CODE = "RECONCILED";
    static final String UNRESOLVED_CODE = "RECONCILED_UNRESOLVED";
    static final String UNRESOLVED_PREFIX = "reconciled-";

    private LedgerTransactionIds() {
    }

    /** 원장 이력의 가장 최근 항목이 가진 트랜잭션 ID. 이력이 없거나 조회에 실패하면 empty. */
    static Optional<String> latest(BlockchainRegistryPort registry, CredentialRegistryReference reference) {
        try {
            List<BlockchainHistoryEntry> history = registry.getCredentialHistory(
                reference, new BlockchainHistoryQuery(50, 0, false));
            return history.stream()
                .max(Comparator.comparing(BlockchainHistoryEntry::timestamp))
                .map(BlockchainHistoryEntry::transactionId)
                .filter(id -> !id.isBlank());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    static String unresolved(UUID transactionId) {
        return UNRESOLVED_PREFIX + transactionId;
    }
}
