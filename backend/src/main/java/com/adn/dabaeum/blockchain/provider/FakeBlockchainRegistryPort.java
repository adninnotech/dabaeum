package com.adn.dabaeum.blockchain.provider;

import com.adn.dabaeum.blockchain.domain.BlockchainHistoryEntry;
import com.adn.dabaeum.blockchain.domain.BlockchainHistoryQuery;
import com.adn.dabaeum.blockchain.domain.BlockchainReceipt;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryException;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryCreate;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReissue;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryState;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryUpdate;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** 개발·자동 테스트에서 외부 원장 연결 없이 상태 전이를 재현하는 Fake Provider다. */
public final class FakeBlockchainRegistryPort implements BlockchainRegistryPort {

    private final Map<String, CredentialRegistryState> states = new LinkedHashMap<>();
    private final Map<String, List<BlockchainHistoryEntry>> histories = new LinkedHashMap<>();
    private final AtomicLong transactionSequence = new AtomicLong();

    @Override
    public synchronized BlockchainReceipt createCredentialState(CredentialRegistryCreate command) {
        String key = command.reference().chainKey();
        if (states.containsKey(key)) {
            throw new BlockchainRegistryException("BLOCKCHAIN_ALREADY_EXISTS");
        }
        CredentialRegistryState state = state(
            command.reference(), command.schemaVersion(), command.status(), command.vcHash(),
            command.eventTime());
        String transactionId = nextTransactionId();
        states.put(key, state);
        appendHistory(key, transactionId, state, command.eventTime());
        return receipt(command.reference(), transactionId, command.eventTime());
    }

    @Override
    public synchronized CredentialRegistryState getCredentialState(
        CredentialRegistryReference reference
    ) {
        CredentialRegistryState state = states.get(reference.chainKey());
        if (state == null) {
            throw new BlockchainRegistryException("BLOCKCHAIN_NOT_FOUND");
        }
        return state;
    }

    @Override
    public synchronized BlockchainReceipt updateCredentialState(CredentialRegistryUpdate command) {
        String key = command.reference().chainKey();
        if (!states.containsKey(key)) {
            throw new BlockchainRegistryException("BLOCKCHAIN_NOT_FOUND");
        }
        CredentialRegistryState state = state(
            command.reference(), command.schemaVersion(), command.status(), command.vcHash(),
            command.eventTime());
        String transactionId = nextTransactionId();
        states.put(key, state);
        appendHistory(key, transactionId, state, command.eventTime());
        return receipt(command.reference(), transactionId, command.eventTime());
    }

    @Override
    public synchronized BlockchainReceipt reissueCredentialState(
        CredentialRegistryReissue command
    ) {
        String previousKey = command.previousReference().chainKey();
        String replacementKey = command.replacementReference().chainKey();
        CredentialRegistryState previous = states.get(previousKey);
        if (previous == null) {
            throw new BlockchainRegistryException("BLOCKCHAIN_NOT_FOUND");
        }
        if (states.containsKey(replacementKey)) {
            throw new BlockchainRegistryException("BLOCKCHAIN_ALREADY_EXISTS");
        }

        String transactionId = nextTransactionId();
        CredentialRegistryState revoked = state(
            command.previousReference(), previous.schemaVersion(), RegistryStatus.REVOKED,
            previous.vcHash(), command.revokedAt());
        CredentialRegistryState replacement = state(
            command.replacementReference(), command.schemaVersion(), RegistryStatus.ACTIVE,
            command.vcHash(), command.eventTime());
        states.put(previousKey, revoked);
        states.put(replacementKey, replacement);
        appendHistory(previousKey, transactionId, revoked, command.revokedAt());
        appendHistory(replacementKey, transactionId, replacement, command.eventTime());
        return receipt(command.replacementReference(), transactionId, command.eventTime());
    }

    @Override
    public synchronized List<BlockchainHistoryEntry> getCredentialHistory(
        CredentialRegistryReference reference,
        BlockchainHistoryQuery query
    ) {
        List<BlockchainHistoryEntry> values = new ArrayList<>(
            histories.getOrDefault(reference.chainKey(), List.of()));
        if (query.reverse()) {
            Collections.reverse(values);
        }
        int start = Math.min(query.offset(), values.size());
        int end = Math.min(start + query.limit(), values.size());
        return List.copyOf(values.subList(start, end));
    }

    private static CredentialRegistryState state(
        CredentialRegistryReference reference,
        int schemaVersion,
        RegistryStatus status,
        String vcHash,
        Instant eventTime
    ) {
        return new CredentialRegistryState(
            reference.chainKey(), schemaVersion, status, vcHash, eventTime,
            reference.provider());
    }

    private void appendHistory(
        String key,
        String transactionId,
        CredentialRegistryState state,
        Instant timestamp
    ) {
        histories.computeIfAbsent(key, ignored -> new ArrayList<>())
            .add(new BlockchainHistoryEntry(transactionId, state, timestamp, false));
    }

    private String nextTransactionId() {
        return "fake-tx-" + transactionSequence.incrementAndGet();
    }

    private static BlockchainReceipt receipt(
        CredentialRegistryReference reference,
        String transactionId,
        Instant confirmedAt
    ) {
        return new BlockchainReceipt(
            reference.provider(), transactionId, null, null, confirmedAt, true, "VALID");
    }
}
