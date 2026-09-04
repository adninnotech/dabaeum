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
import com.adn.dabaeum.credential.application.CredentialHashService;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import com.adn.dabaeum.fabric.domain.BlockchainTransaction;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import tools.jackson.databind.ObjectMapper;

class CredentialIssueProviderWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    @Test
    void currentIssueRegistersFullCompactJwsHashThroughRegistryPort() {
        Credential credential = new Credential(
            UUID.randomUUID(), UUID.randomUUID(), null, "CERT-CURRENT", 1,
            null, null, "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
            null, null, null, null, "CHAINKEY00000001", "COMPACT_JWS_SHA256_V1",
            null, null, null, null, null, NOW, NOW);
        InMemoryCredentialRepository credentials = new InMemoryCredentialRepository(credential);
        InMemoryTransactionRepository transactions = new InMemoryTransactionRepository(
            pendingTransaction(credential.id(), "FABRIC_POC"));
        RecordingRegistry registry = new RecordingRegistry();
        DefaultCredentialFabricWorker worker = new DefaultCredentialFabricWorker(
            transactions,
            credentials,
            (credentialId, issuedAt) -> new CredentialFabricIssuanceContext(
                new CredentialDocument("{\"id\":\"urn:uuid:" + credentialId + "\"}"),
                "urn:dabaeum:institution:issuer",
                "urn:dabaeum:user:subject",
                issuedAt),
            new CredentialProofService() {
                @Override public SignedCredentialEnvelope sign(CredentialDocument document) {
                    return new SignedCredentialEnvelope(
                        "application/vc+jws", "header.payload.signature");
                }
                @Override public CredentialDocument verify(SignedCredentialEnvelope envelope) {
                    throw new UnsupportedOperationException();
                }
            },
            new CredentialHashService(new ObjectMapper()),
            registry,
            new NoOpTransactionManager());

        assertThat(worker.processBatch(10, NOW)).isEqualTo(1);

        assertThat(registry.created).singleElement().satisfies(command -> {
            assertThat(command.reference()).isEqualTo(new CredentialRegistryReference(
                BlockchainProvider.FABRIC_POC, "CHAINKEY00000001", null));
            assertThat(command.status()).isEqualTo(RegistryStatus.ACTIVE);
            assertThat(command.vcHash())
                .isEqualTo("256d04db4e5e4ac308751ed0885b722b758630567c53a7125ed9fbd068e5c3f6");
            assertThat(command.hashVersion()).isEqualTo("COMPACT_JWS_SHA256_V1");
            assertThat(command.legacyDetails()).isNull();
        });
        assertThat(credentials.value.status()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(transactions.value.status()).isEqualTo(BlockchainTransactionStatus.CONFIRMED);
        assertThat(transactions.value.transactionId()).isEqualTo("tx-provider-001");
    }

    private static BlockchainTransaction pendingTransaction(UUID credentialId, String network) {
        return new BlockchainTransaction(
            UUID.randomUUID(), "CREDENTIAL", credentialId, network,
            BlockchainTransactionType.VC_ANCHOR, "issue-provider-key", UUID.randomUUID(),
            "a".repeat(64), null, BlockchainTransactionStatus.PENDING, null, null,
            NOW, null, 0, null, null, NOW, NOW);
    }

    private static final class RecordingRegistry implements BlockchainRegistryPort {
        private final java.util.ArrayList<CredentialRegistryCreate> created = new java.util.ArrayList<>();

        @Override public BlockchainReceipt createCredentialState(CredentialRegistryCreate command) {
            created.add(command);
            return new BlockchainReceipt(
                BlockchainProvider.FABRIC_POC, "tx-provider-001", null, null, NOW, true, "VALID");
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
        @Override public List<BlockchainHistoryEntry> getCredentialHistory(
            CredentialRegistryReference reference, BlockchainHistoryQuery query) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryCredentialRepository implements CredentialRepository {
        private Credential value;
        private InMemoryCredentialRepository(Credential value) { this.value = value; }
        @Override public Optional<Credential> findById(UUID id) {
            return value.id().equals(id) ? Optional.of(value) : Optional.empty();
        }
        @Override public Optional<Credential> findByCredentialNo(String credentialNo) { return Optional.empty(); }
        @Override public Optional<Credential> findByCredentialHash(String credentialHash) { return Optional.empty(); }
        @Override public Optional<Credential> findByIdForUpdate(UUID id) { return findById(id); }
        @Override public Optional<Credential> findActiveByGroupId(UUID id) { return Optional.empty(); }
        @Override public List<Credential> findByUserId(UUID id, int limit, int offset, String sort) { return List.of(); }
        @Override public long countByUserId(UUID id) { return 0; }
        @Override public int nextVersionForUpdate(UUID id) { return 1; }
        @Override public void insert(Credential credential) { value = credential; }
        @Override public boolean insertIfChainKeyAvailable(Credential credential) {
            value = credential;
            return true;
        }
        @Override public void update(Credential credential) { value = credential; }
    }

    private static final class InMemoryTransactionRepository
        implements BlockchainTransactionRepository {
        private BlockchainTransaction value;
        private InMemoryTransactionRepository(BlockchainTransaction value) { this.value = value; }
        @Override public List<BlockchainTransaction> claimDue(
            List<String> networks, int limit, Instant now) {
            assertThat(networks).containsExactly("FABRIC_POC", "DABAEUM_FABRIC");
            value = value.claim(now);
            return List.of(value);
        }
        @Override public List<BlockchainTransaction> claimStale(
            List<String> networks, int limit, Instant now, Instant staleBefore) {
            return List.of();
        }
        @Override public Optional<BlockchainTransaction> findById(UUID id) { return Optional.of(value); }
        @Override public Optional<BlockchainTransaction> findByIdempotencyKey(String network, String key) {
            return Optional.of(value);
        }
        @Override public void insert(BlockchainTransaction transaction) { value = transaction; }
        @Override public void update(BlockchainTransaction transaction) { value = transaction; }
    }

    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
