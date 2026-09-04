package com.adn.dabaeum.fabric.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.blockchain.domain.BlockchainHistoryEntry;
import com.adn.dabaeum.blockchain.domain.BlockchainHistoryQuery;
import com.adn.dabaeum.blockchain.domain.BlockchainProvider;
import com.adn.dabaeum.blockchain.domain.BlockchainReceipt;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryException;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryCreate;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReissue;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryState;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryUpdate;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransaction;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionType;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CredentialFabricReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    void claimsLegacyAndCurrentStaleTransactionsThroughProviderNeutralPort() {
        Credential credential = currentCredential(
            "CURRENTKEY000001", CredentialStatus.ISSUING, null);
        BlockchainTransaction transaction = transaction(
            credential, "FABRIC_POC", BlockchainTransactionType.VC_ANCHOR, 0);
        InMemoryTransactions transactions = new InMemoryTransactions(transaction);
        InMemoryCredentials credentials = new InMemoryCredentials(credential);
        RecordingRegistry registry = new RecordingRegistry();
        registry.put(credential, RegistryStatus.ACTIVE);

        int count = new DefaultCredentialFabricReconciler(
            transactions, credentials, registry).reconcileBatch(10, NOW);

        assertThat(count).isEqualTo(1);
        assertThat(transactions.claimedNetworks)
            .containsExactly("FABRIC_POC", "DABAEUM_FABRIC");
        assertThat(registry.references).singleElement().satisfies(reference -> {
            assertThat(reference.dataKey()).isEqualTo(credential.chainKey());
            assertThat(reference.legacyCredentialNo()).isNull();
        });
        assertThat(credentials.current.status()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(transactions.current.status())
            .isEqualTo(BlockchainTransactionStatus.CONFIRMED);
    }

    @Test
    void reconcilesLegacyRevokeWithoutOverwritingConflictingState() {
        Credential credential = legacyCredential("CERT-R1", CredentialStatus.ISSUED, null);
        BlockchainTransaction transaction = transaction(
            credential, "DABAEUM_FABRIC", BlockchainTransactionType.VC_REVOKE, 0);
        InMemoryTransactions transactions = new InMemoryTransactions(transaction);
        InMemoryCredentials credentials = new InMemoryCredentials(credential);
        RecordingRegistry registry = new RecordingRegistry();
        registry.put(credential, RegistryStatus.REVOKED);

        new DefaultCredentialFabricReconciler(
            transactions, credentials, registry).reconcileBatch(10, NOW);

        assertThat(credentials.current.status()).isEqualTo(CredentialStatus.REVOKED);
        assertThat(transactions.current.status())
            .isEqualTo(BlockchainTransactionStatus.CONFIRMED);
    }

    @Test
    void currentReissueFinalizesOnlyAfterOldSupersededAndReplacementActive() {
        Credential previous = currentCredential(
            "CURRENTOLDKEY001", CredentialStatus.ISSUED, null);
        Credential replacement = currentCredential(
            "CURRENTNEWKEY001", CredentialStatus.ISSUING, previous.id());
        BlockchainTransaction transaction = transaction(
            replacement, "FABRIC_POC", BlockchainTransactionType.VC_REISSUE, 0);
        InMemoryTransactions transactions = new InMemoryTransactions(transaction);
        InMemoryCredentials credentials = new InMemoryCredentials(previous, replacement);
        RecordingRegistry registry = new RecordingRegistry();
        registry.put(previous, RegistryStatus.SUPERSEDED);
        registry.put(replacement, RegistryStatus.ACTIVE);

        new DefaultCredentialFabricReconciler(
            transactions, credentials, registry).reconcileBatch(10, NOW);

        assertThat(credentials.findById(previous.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.SUPERSEDED);
        assertThat(credentials.findById(replacement.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.ISSUED);
        assertThat(transactions.current.status())
            .isEqualTo(BlockchainTransactionStatus.CONFIRMED);
    }

    @Test
    void currentPartialReissueIsRetriedAndBecomesFailedAfterFiveAttempts() {
        Credential previous = currentCredential(
            "CURRENTOLDKEY001", CredentialStatus.ISSUED, null);
        Credential replacement = currentCredential(
            "CURRENTNEWKEY001", CredentialStatus.ISSUING, previous.id());
        BlockchainTransaction transaction = transaction(
            replacement, "FABRIC_POC", BlockchainTransactionType.VC_REISSUE, 4);
        InMemoryTransactions transactions = new InMemoryTransactions(transaction);
        InMemoryCredentials credentials = new InMemoryCredentials(previous, replacement);
        RecordingRegistry registry = new RecordingRegistry();
        registry.put(previous, RegistryStatus.SUPERSEDED);

        new DefaultCredentialFabricReconciler(
            transactions, credentials, registry).reconcileBatch(10, NOW);

        assertThat(transactions.current.status())
            .isEqualTo(BlockchainTransactionStatus.FAILED);
        assertThat(transactions.current.retryCount()).isEqualTo(5);
        assertThat(transactions.current.errorCode())
            .isEqualTo("BLOCKCHAIN_NOT_FOUND");
        assertThat(credentials.findById(previous.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.ISSUED);
        assertThat(credentials.findById(replacement.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.FAILED);
    }

    @Test
    void ledgerStatusMismatchFailsWithoutChangingCredential() {
        Credential credential = legacyCredential("CERT-R2", CredentialStatus.ISSUED, null);
        BlockchainTransaction transaction = transaction(
            credential, "DABAEUM_FABRIC", BlockchainTransactionType.VC_REVOKE, 0);
        InMemoryTransactions transactions = new InMemoryTransactions(transaction);
        InMemoryCredentials credentials = new InMemoryCredentials(credential);
        RecordingRegistry registry = new RecordingRegistry();
        registry.put(credential, RegistryStatus.ACTIVE);

        new DefaultCredentialFabricReconciler(
            transactions, credentials, registry).reconcileBatch(10, NOW);

        assertThat(credentials.current.status()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(transactions.current.status())
            .isEqualTo(BlockchainTransactionStatus.FAILED);
        assertThat(transactions.current.errorCode())
            .isEqualTo("BLOCKCHAIN_LEDGER_CONFLICT");
    }

    @Test
    void retriesOnlyAllowlistedTransientReadFailuresAndStopsAtFiveAttempts() {
        for (String code : List.of(
            "BLOCKCHAIN_NOT_FOUND", "BLOCKCHAIN_READ_FAILED",
            "BLOCKCHAIN_CONNECTION_FAILED")) {
            Credential firstCredential = currentCredential(
                "CURRENTKEY000001", CredentialStatus.ISSUING, null);
            InMemoryTransactions firstTransactions = new InMemoryTransactions(
                transaction(firstCredential, "FABRIC_POC", BlockchainTransactionType.VC_ANCHOR, 0));
            RecordingRegistry firstRegistry = new RecordingRegistry();
            firstRegistry.failureCode = code;

            new DefaultCredentialFabricReconciler(
                firstTransactions, new InMemoryCredentials(firstCredential), firstRegistry)
                .reconcileBatch(10, NOW);

            assertThat(firstTransactions.current.status())
                .as(code).isEqualTo(BlockchainTransactionStatus.PENDING);
            assertThat(firstTransactions.current.retryCount()).as(code).isEqualTo(1);

            Credential finalCredential = currentCredential(
                "CURRENTKEY000002", CredentialStatus.ISSUING, null);
            InMemoryTransactions finalTransactions = new InMemoryTransactions(
                transaction(finalCredential, "FABRIC_POC", BlockchainTransactionType.VC_ANCHOR, 4));
            RecordingRegistry finalRegistry = new RecordingRegistry();
            finalRegistry.failureCode = code;

            new DefaultCredentialFabricReconciler(
                finalTransactions, new InMemoryCredentials(finalCredential), finalRegistry)
                .reconcileBatch(10, NOW);

            assertThat(finalTransactions.current.status())
                .as(code).isEqualTo(BlockchainTransactionStatus.FAILED);
            assertThat(finalTransactions.current.retryCount()).as(code).isEqualTo(5);
        }
    }

    @Test
    void permanentOrUnknownProviderFailureIsImmediatelyFailed() {
        for (String code : List.of(
            "BLOCKCHAIN_RESPONSE_INVALID", "BLOCKCHAIN_LEDGER_CONFLICT", "UNKNOWN_CODE")) {
            Credential credential = currentCredential(
                "CURRENTKEY000001", CredentialStatus.ISSUING, null);
            InMemoryTransactions transactions = new InMemoryTransactions(
                transaction(credential, "FABRIC_POC", BlockchainTransactionType.VC_ANCHOR, 0));
            RecordingRegistry registry = new RecordingRegistry();
            registry.failureCode = code;

            new DefaultCredentialFabricReconciler(
                transactions, new InMemoryCredentials(credential), registry)
                .reconcileBatch(10, NOW);

            assertThat(transactions.current.status())
                .as(code).isEqualTo(BlockchainTransactionStatus.FAILED);
            assertThat(transactions.current.retryCount()).as(code).isZero();
        }
    }

    private static Credential legacyCredential(
        String credentialNo, CredentialStatus status, UUID previousId
    ) {
        return credential(credentialNo, null, null, status, previousId);
    }

    private static Credential currentCredential(
        String chainKey, CredentialStatus status, UUID previousId
    ) {
        return credential("CERT-" + chainKey, chainKey,
            "COMPACT_JWS_SHA256_V1", status, previousId);
    }

    private static Credential credential(
        String credentialNo,
        String chainKey,
        String hashVersion,
        CredentialStatus status,
        UUID previousId
    ) {
        return new Credential(
            UUID.randomUUID(), UUID.randomUUID(), previousId, credentialNo,
            previousId == null ? 1 : 2, "urn:issuer", "urn:subject",
            "LIFELONG_EDUCATION_COMPLETION", status, NOW.minusSeconds(180), null,
            "{\"mediaType\":\"application/vc+jws\",\"compactJws\":\"header.payload.signature\"}",
            HASH, chainKey, hashVersion, NOW.minusSeconds(180), null, null,
            null, null, NOW.minusSeconds(180), NOW.minusSeconds(120));
    }

    private static BlockchainTransaction transaction(
        Credential credential,
        String network,
        BlockchainTransactionType type,
        int retryCount
    ) {
        return new BlockchainTransaction(
            UUID.randomUUID(), "CREDENTIAL", credential.id(), network, type,
            "reconcile-" + UUID.randomUUID(), UUID.randomUUID(), "b".repeat(64),
            null, BlockchainTransactionStatus.PROCESSING, null, null,
            NOW.minusSeconds(120), null, retryCount, null, null,
            NOW.minusSeconds(120), NOW.minusSeconds(120), "policy correction");
    }

    private static final class RecordingRegistry implements BlockchainRegistryPort {
        private final Map<String, CredentialRegistryState> states = new HashMap<>();
        private final List<CredentialRegistryReference> references = new java.util.ArrayList<>();
        private String failureCode;

        private void put(Credential credential, RegistryStatus status) {
            CredentialRegistryReference reference = reference(credential);
            states.put(reference.chainKey(), new CredentialRegistryState(
                reference.chainKey(), 1, status, credential.vcHash(), NOW,
                BlockchainProvider.FABRIC_POC));
        }

        @Override public CredentialRegistryState getCredentialState(
            CredentialRegistryReference reference
        ) {
            references.add(reference);
            if (failureCode != null) throw new BlockchainRegistryException(failureCode);
            CredentialRegistryState state = states.get(reference.chainKey());
            if (state == null) throw new BlockchainRegistryException("BLOCKCHAIN_NOT_FOUND");
            return state;
        }

        @Override public BlockchainReceipt createCredentialState(CredentialRegistryCreate command) {
            throw new UnsupportedOperationException();
        }
        @Override public BlockchainReceipt updateCredentialState(CredentialRegistryUpdate command) {
            throw new UnsupportedOperationException();
        }
        @Override public BlockchainReceipt reissueCredentialState(CredentialRegistryReissue command) {
            throw new UnsupportedOperationException();
        }
        @Override public List<BlockchainHistoryEntry> getCredentialHistory(
            CredentialRegistryReference reference, BlockchainHistoryQuery query
        ) { return List.of(); }

        private static CredentialRegistryReference reference(Credential credential) {
            return credential.chainKey() == null
                ? new CredentialRegistryReference(
                    BlockchainProvider.FABRIC_POC, null, credential.credentialNo())
                : new CredentialRegistryReference(
                    BlockchainProvider.FABRIC_POC, credential.chainKey(), null);
        }
    }

    private static final class InMemoryTransactions implements BlockchainTransactionRepository {
        private BlockchainTransaction current;
        private List<String> claimedNetworks = List.of();

        private InMemoryTransactions(BlockchainTransaction current) {
            this.current = current;
        }

        @Override public List<BlockchainTransaction> claimDue(
            List<String> networks, int limit, Instant now
        ) { return List.of(); }

        @Override public List<BlockchainTransaction> claimStale(
            List<String> networks, int limit, Instant now, Instant staleBefore
        ) {
            claimedNetworks = List.copyOf(networks);
            return current.status() == BlockchainTransactionStatus.PROCESSING
                ? List.of(current) : List.of();
        }

        @Override public Optional<BlockchainTransaction> findById(UUID id) {
            return Optional.of(current);
        }
        @Override public Optional<BlockchainTransaction> findByIdempotencyKey(
            String network, String key
        ) { return Optional.of(current); }
        @Override public void insert(BlockchainTransaction value) { current = value; }
        @Override public void update(BlockchainTransaction value) { current = value; }
    }

    private static final class InMemoryCredentials implements CredentialRepository {
        private Credential current;
        private final Map<UUID, Credential> byId = new HashMap<>();

        private InMemoryCredentials(Credential... values) {
            for (Credential value : values) update(value);
        }

        @Override public Optional<Credential> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }
        @Override public Optional<Credential> findByCredentialNo(String credentialNo) {
            return Optional.empty();
        }
        @Override public Optional<Credential> findByCredentialHash(String credentialHash) {
            return Optional.empty();
        }
        @Override public Optional<Credential> findByIdForUpdate(UUID id) { return findById(id); }
        @Override public Optional<Credential> findActiveByGroupId(UUID id) { return Optional.empty(); }
        @Override public List<Credential> findByUserId(
            UUID id, int limit, int offset, String sort
        ) { return List.of(); }
        @Override public long countByUserId(UUID id) { return 0; }
        @Override public int nextVersionForUpdate(UUID id) { return 1; }
        @Override public void insert(Credential value) { update(value); }
        @Override public boolean insertIfChainKeyAvailable(Credential value) {
            insert(value);
            return true;
        }
        @Override public void update(Credential value) {
            current = value;
            byId.put(value.id(), value);
        }
    }
}
