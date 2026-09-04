package com.adn.dabaeum.fabric.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionStatus;
import tools.jackson.databind.ObjectMapper;

class CredentialFabricWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    @Test
    void rejectsMissingRequiredDependency() {
        Fixture fixture = new Fixture(new RecordingRegistry());

        assertThatThrownBy(() -> new DefaultCredentialFabricWorker(
            fixture.transactions,
            null,
            (credentialId, issuedAt) -> new CredentialFabricIssuanceContext(
                new CredentialDocument("{\"id\":\"urn:uuid:" + credentialId + "\"}"),
                "urn:dabaeum:institution:" + UUID.randomUUID(),
                "urn:dabaeum:user:" + UUID.randomUUID(),
                issuedAt
            ),
            fixture.proof,
            new CredentialHashService(new ObjectMapper()),
            fixture.workerRegistry,
            new RecordingTransactionManager()
        )).isInstanceOf(NullPointerException.class)
            .hasMessage("credentialRepository");
    }

    @Test
    void issuesOnlyFixedPublicArgumentsAndConfirmsCredentialAndTransaction() {
        Fixture fixture = new Fixture(new RecordingRegistry());

        assertThat(fixture.worker().processBatch(10, NOW)).isEqualTo(1);

        assertThat(fixture.credentials.credential.status()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(fixture.transactions.transaction.status()).isEqualTo(BlockchainTransactionStatus.CONFIRMED);
        CredentialRegistryCreate command = fixture.registry.createCommands().get(0);
        assertThat(command.legacyDetails().subjectHash()).startsWith("sha256:");
        assertThat(command.legacyDetails().issuerHash()).startsWith("sha256:");
        assertThat(command.vcHash()).isEqualTo(fixture.credentials.credential.vcHash());
        assertThat(command.vcHash())
            .isEqualTo("08f8f312a0e580f60047f484af39f32c76ea9b2da3c5796c7eae8b950c7039a3");
        assertThat(command.hashVersion()).isEqualTo("ENVELOPE_SHA256_V0");
        assertThat(command.reference().legacyCredentialNo()).isEqualTo("CERT-001");
        assertThat(command.legacyDetails().subjectHash()).doesNotContain("urn:");
    }

    @Test
    void confirmsIssueWithoutResubmittingWhenLedgerAlreadyHasMatchingAnchor() {
        // commit 확인이 타임아웃되면 원장에는 반영됐어도 재시도가 걸린다. 그때 CreateData 는
        // 계속 "already exists" 로 거부되므로, 이를 그대로 실패로 처리하면 재시도를 모두
        // 소진하고 원장은 ACTIVE 인데 DB 는 FAILED 인 상태가 영구히 남는다.
        // Reconciler 는 PROCESSING 만 회수하므로 이 건을 복구하지 못한다.
        Fixture fixture = new Fixture(new AlreadyAnchoredRegistry());

        assertThat(fixture.worker().processBatch(10, NOW)).isEqualTo(1);

        assertThat(fixture.credentials.credential.status()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(fixture.transactions.transaction.status())
            .isEqualTo(BlockchainTransactionStatus.CONFIRMED);
        assertThat(fixture.transactions.transaction.retryCount()).isZero();
    }

    @Test
    void proofFailureDoesNotCallGatewayAndFailsBothRecords() {
        Fixture fixture = new Fixture(new RecordingRegistry());
        fixture.proof.fail = true;

        assertThat(fixture.worker().processBatch(10, NOW)).isEqualTo(1);

        assertThat(fixture.registry.createCommands()).isEmpty();
        assertThat(fixture.credentials.credential.status()).isEqualTo(CredentialStatus.FAILED);
        assertThat(fixture.transactions.transaction.status()).isEqualTo(BlockchainTransactionStatus.FAILED);
    }

    @Test
    void retryableFailureReusesSavedEnvelopeAndHash() {
        Fixture fixture = new Fixture(new FailOnceRegistry());

        fixture.worker().processBatch(10, NOW);
        String firstPayload = fixture.credentials.credential.vcPayload();
        String firstHash = fixture.credentials.credential.vcHash();
        Instant firstIssuedAt = fixture.registry.createCommands().get(0).eventTime();

        assertThat(fixture.credentials.credential.status()).isEqualTo(CredentialStatus.ISSUING);
        assertThat(fixture.transactions.transaction.status()).isEqualTo(BlockchainTransactionStatus.PENDING);
        assertThat(fixture.transactions.transaction.nextRetryAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(fixture.proof.signCalls).isEqualTo(1);

        fixture.worker().processBatch(10, NOW.plusSeconds(30));

        assertThat(fixture.credentials.credential.status()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(fixture.credentials.credential.vcPayload()).isEqualTo(firstPayload);
        assertThat(fixture.credentials.credential.vcHash()).isEqualTo(firstHash);
        assertThat(fixture.proof.signCalls).isEqualTo(1);
        assertThat(fixture.registry.createCommands().get(1).eventTime()).isEqualTo(firstIssuedAt);
    }

    @Test
    void invalidCommitFailsBothRecords() {
        Fixture fixture = new Fixture(new UnconfirmedRegistry());

        fixture.worker().processBatch(10, NOW);

        assertThat(fixture.credentials.credential.status()).isEqualTo(CredentialStatus.FAILED);
        assertThat(fixture.transactions.transaction.status()).isEqualTo(BlockchainTransactionStatus.FAILED);
    }

    @Test
    void finalizationFailureLeavesIssuingAndProcessingForReconciliation() {
        Fixture fixture = new Fixture(new RecordingRegistry());
        fixture.credentials.failIssuedUpdate = true;

        assertThatThrownBy(() -> fixture.worker().processBatch(10, NOW))
            .isInstanceOf(IllegalStateException.class);

        assertThat(fixture.credentials.credential.status()).isEqualTo(CredentialStatus.ISSUING);
        assertThat(fixture.transactions.transaction.status()).isEqualTo(BlockchainTransactionStatus.PROCESSING);
    }

    @Test
    void keepsFabricSubmissionOutsideShortDatabaseTransactions() {
        RecordingTransactionManager manager = new RecordingTransactionManager();
        RecordingRegistry registry = new RecordingRegistry() {
            @Override public BlockchainReceipt createCredentialState(CredentialRegistryCreate command) {
                assertThat(manager.active).isFalse();
                return super.createCredentialState(command);
            }
        };
        Fixture fixture = new Fixture(registry);

        assertThat(fixture.worker(manager).processBatch(10, NOW)).isEqualTo(1);
        assertThat(manager.begins).isGreaterThanOrEqualTo(3);
        assertThat(manager.commits).isEqualTo(manager.begins);
    }

    @Test
    void revokesOnlyAfterFabricCommit() {
        Credential issued = issuedCredential();
        LifecycleRegistry registry = new LifecycleRegistry();
        Fixture fixture = new Fixture(issued, pendingTransaction(issued.id(), BlockchainTransactionType.VC_REVOKE,
            "administrative correction"), registry);

        fixture.worker().processBatch(10, NOW);

        assertThat(fixture.credentials.findById(issued.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.REVOKED);
        assertThat(fixture.transactions.transaction.status()).isEqualTo(BlockchainTransactionStatus.CONFIRMED);
        assertThat(registry.updateCommands()).singleElement().satisfies(command -> {
            assertThat(command.reference().legacyCredentialNo()).isEqualTo(issued.credentialNo());
            assertThat(command.eventTime()).isEqualTo(NOW);
        });
    }

    @Test
    void revokesCurrentCredentialByUpdatingTheSameStorageKeyToR() {
        Credential issued = currentIssuedCredential("CURRENTKEY000001");
        LifecycleRegistry registry = new LifecycleRegistry();
        BlockchainTransaction transaction = new BlockchainTransaction(
            UUID.randomUUID(), "CREDENTIAL", issued.id(), "FABRIC_POC",
            BlockchainTransactionType.VC_REVOKE, "current-revoke", UUID.randomUUID(),
            "a".repeat(64), null, BlockchainTransactionStatus.PENDING, null, null,
            NOW, null, 0, null, null, NOW, NOW, "administrative correction");
        Fixture fixture = new Fixture(issued, transaction, registry);

        fixture.worker().processBatch(10, NOW);

        assertThat(registry.updateCommands()).singleElement().satisfies(command -> {
            assertThat(command.reference().dataKey()).isEqualTo(issued.chainKey());
            assertThat(command.status()).isEqualTo(RegistryStatus.REVOKED);
            assertThat(command.hashVersion()).isEqualTo("COMPACT_JWS_SHA256_V1");
        });
        assertThat(fixture.credentials.findById(issued.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.REVOKED);
    }

    @Test
    void confirmsLegacyRevokeWithoutResubmittingWhenLedgerIsAlreadyRevoked() {
        Credential issued = issuedCredential();
        LedgerCommittedLegacyRegistry registry = new LedgerCommittedLegacyRegistry(
            issued, null, true);
        Fixture fixture = new Fixture(
            issued, pendingTransaction(issued.id(), BlockchainTransactionType.VC_REVOKE,
                "administrative correction"), registry);

        fixture.worker().processBatch(10, NOW);

        assertThat(registry.updateCalls).isZero();
        assertThat(fixture.credentials.findById(issued.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.REVOKED);
        assertThat(fixture.transactions.transaction.status())
            .isEqualTo(BlockchainTransactionStatus.CONFIRMED);
    }

    @Test
    void reissuesAndConfirmsOldAndNewCredentialInOneCompletionPhase() {
        Credential previous = issuedCredential();
        Credential replacement = new Credential(UUID.randomUUID(), previous.credentialGroupId(), previous.id(),
            "CERT-002", 2, null, null, "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
            null, null, null, null, null, null, null, null, null, NOW, NOW);
        LifecycleRegistry registry = new LifecycleRegistry();
        Fixture fixture = new Fixture(previous, replacement,
            pendingTransaction(replacement.id(), BlockchainTransactionType.VC_REISSUE, "reissue requested"), registry);

        fixture.worker().processBatch(10, NOW);

        assertThat(fixture.credentials.findById(previous.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.SUPERSEDED);
        assertThat(fixture.credentials.findById(replacement.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.ISSUED);
        assertThat(fixture.transactions.transaction.status()).isEqualTo(BlockchainTransactionStatus.CONFIRMED);
        assertThat(registry.reissueCommands()).singleElement().satisfies(command -> {
            assertThat(command.previousReference().legacyCredentialNo()).isEqualTo(previous.credentialNo());
            assertThat(command.replacementReference().legacyCredentialNo()).isEqualTo(replacement.credentialNo());
        });
    }

    @Test
    void resumesCurrentReissueAfterReplacementCreateFailsWithoutDeletingLedgerState() {
        Credential previous = currentIssuedCredential("CURRENTOLDKEY001");
        Credential replacement = new Credential(
            UUID.randomUUID(), previous.credentialGroupId(), previous.id(), "CERT-CURRENT-002", 2,
            null, null, "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
            null, null, null, null, "CURRENTNEWKEY001", "COMPACT_JWS_SHA256_V1",
            null, null, null, null, null, NOW, NOW);
        CurrentReissueRegistry registry = new CurrentReissueRegistry(
            previous.chainKey(), replacement.chainKey());
        BlockchainTransaction transaction = new BlockchainTransaction(
            UUID.randomUUID(), "CREDENTIAL", replacement.id(), "FABRIC_POC",
            BlockchainTransactionType.VC_REISSUE, "current-reissue", UUID.randomUUID(),
            "a".repeat(64), null, BlockchainTransactionStatus.PENDING, null, null,
            NOW, null, 0, null, null, NOW, NOW, "corrected completion");
        Fixture fixture = new Fixture(previous, replacement, transaction, registry);

        fixture.worker().processBatch(10, NOW);

        assertThat(fixture.transactions.transaction.status())
            .isEqualTo(BlockchainTransactionStatus.PENDING);
        assertThat(fixture.transactions.transaction.retryCount()).isEqualTo(1);
        assertThat(registry.supersedeCommands).hasSize(1);
        assertThat(registry.createAttempts).isEqualTo(1);
        assertThat(registry.deleteCalls).isZero();

        fixture.worker().processBatch(10, NOW.plusSeconds(30));

        assertThat(registry.supersedeCommands).hasSize(1);
        assertThat(registry.createAttempts).isEqualTo(2);
        assertThat(registry.deleteCalls).isZero();
        assertThat(fixture.credentials.findById(previous.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.SUPERSEDED);
        assertThat(fixture.credentials.findById(replacement.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.ISSUED);
        assertThat(fixture.transactions.transaction.status())
            .isEqualTo(BlockchainTransactionStatus.CONFIRMED);
    }

    @Test
    void confirmsLegacyReissueWithoutResubmittingAfterAnAmbiguousCommit() {
        Credential previous = issuedCredential();
        Credential replacement = new Credential(
            UUID.randomUUID(), previous.credentialGroupId(), previous.id(), "CERT-002", 2,
            null, null, "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
            null, null, null, null, null, "ENVELOPE_SHA256_V0",
            null, null, null, null, null, NOW, NOW);
        LedgerCommittedLegacyRegistry registry = new LedgerCommittedLegacyRegistry(
            previous, replacement, false);
        Fixture fixture = new Fixture(
            previous, replacement,
            pendingTransaction(
                replacement.id(), BlockchainTransactionType.VC_REISSUE, "corrected"),
            registry);

        fixture.worker().processBatch(10, NOW);

        assertThat(registry.reissueCalls).isZero();
        assertThat(fixture.credentials.findById(previous.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.SUPERSEDED);
        assertThat(fixture.credentials.findById(replacement.id()).orElseThrow().status())
            .isEqualTo(CredentialStatus.ISSUED);
        assertThat(fixture.transactions.transaction.status())
            .isEqualTo(BlockchainTransactionStatus.CONFIRMED);
    }

    private static final class Fixture {
        private final InMemoryCredentialRepository credentials = new InMemoryCredentialRepository();
        private final InMemoryTransactionRepository transactions = new InMemoryTransactionRepository();
        private final CountingProof proof = new CountingProof();
        private final RecordingRegistry registry;
        private Credential credential;
        private BlockchainTransaction transaction;
        private Fixture(RecordingRegistry registry) { this((BlockchainRegistryPort) registry); }
        private Fixture(BlockchainRegistryPort registry) {
            credential = pendingCredential(); transaction = pendingTransaction(credential.id());
            credentials.seed(credential); transactions.transaction = transaction;
            this.registry = registry instanceof RecordingRegistry recording
                ? recording : new RecordingRegistry();
            workerRegistry = registry;
        }
        private Fixture(Credential credential, BlockchainTransaction transaction,
                        BlockchainRegistryPort registry) {
            this(credential, null, transaction, registry);
        }
        private Fixture(Credential first, Credential second, BlockchainTransaction transaction,
                        BlockchainRegistryPort registry) {
            credentials.seed(first);
            if (second != null) credentials.seed(second);
            transactions.transaction = transaction;
            this.credential = second == null ? first : second;
            this.transaction = transaction;
            this.registry = registry instanceof RecordingRegistry recording
                ? recording : new RecordingRegistry();
            workerRegistry = registry;
        }
        private BlockchainRegistryPort workerRegistry;
        private CredentialFabricWorker worker() {
            return worker(new RecordingTransactionManager());
        }
        private CredentialFabricWorker worker(PlatformTransactionManager transactionManager) {
            return new DefaultCredentialFabricWorker(transactions, credentials,
                (credentialId, issuedAt) -> new CredentialFabricIssuanceContext(
                    new CredentialDocument("{\"id\":\"urn:uuid:" + credentialId + "\"}"),
                    "urn:dabaeum:institution:" + UUID.randomUUID(),
                    "urn:dabaeum:user:" + UUID.randomUUID(), issuedAt),
                proof, new CredentialHashService(new ObjectMapper()), workerRegistry, transactionManager);
        }
    }

    private static final class RecordingTransactionManager implements PlatformTransactionManager {
        int begins;
        int commits;
        boolean active;

        @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
            begins++;
            active = true;
            return new DefaultTransactionStatus("task7", null, true, true, false, false, false, null);
        }

        @Override public void commit(TransactionStatus status) { commits++; active = false; }
        @Override public void rollback(TransactionStatus status) { active = false; }
    }

    private static Credential pendingCredential() {
        return new Credential(UUID.randomUUID(), UUID.randomUUID(), null, "CERT-001", 1, null, null,
            "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING, null, null, null, null, null,
            null, null, null, null, NOW, NOW);
    }
    private static Credential issuedCredential() {
        return new Credential(UUID.randomUUID(), UUID.randomUUID(), null, "CERT-001", 1,
            "urn:dabaeum:institution:issuer", "urn:dabaeum:user:subject",
            "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.ISSUED, NOW, null,
            "{\"credentialSubject\":{}}", "a".repeat(64), NOW, null, null, null, null, NOW, NOW);
    }
    private static Credential currentIssuedCredential(String chainKey) {
        return new Credential(UUID.randomUUID(), UUID.randomUUID(), null, "CERT-CURRENT-001", 1,
            "urn:dabaeum:institution:issuer", "urn:dabaeum:user:subject",
            "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.ISSUED, NOW, null,
            "{\"mediaType\":\"application/vc+jws\",\"compactJws\":\"header.payload.signature\"}",
            "a".repeat(64), chainKey, "COMPACT_JWS_SHA256_V1", NOW, null, null,
            null, null, NOW, NOW);
    }
    private static BlockchainTransaction pendingTransaction(UUID credentialId) {
        return pendingTransaction(credentialId, BlockchainTransactionType.VC_ANCHOR, null);
    }
    private static BlockchainTransaction pendingTransaction(UUID credentialId,
                                                             BlockchainTransactionType type,
                                                             String reason) {
        return new BlockchainTransaction(UUID.randomUUID(), "CREDENTIAL", credentialId, "DABAEUM_FABRIC",
            type, "key-" + credentialId, UUID.randomUUID(), "a".repeat(64), null,
            BlockchainTransactionStatus.PENDING, null, null, NOW, null, 0, null, null, NOW, NOW, reason);
    }
    private static final class CountingProof implements CredentialProofService {
        int signCalls; boolean fail;
        @Override public SignedCredentialEnvelope sign(CredentialDocument document) {
            signCalls++; if (fail) throw new ProofException(FailureCode.CREDENTIAL_PROOF_GENERATION_FAILED, "masked");
            return new SignedCredentialEnvelope("application/vc+jws", "header.payload.signature");
        }
        @Override public CredentialDocument verify(SignedCredentialEnvelope envelope) { throw new UnsupportedOperationException(); }
    }
    private static class RecordingRegistry implements BlockchainRegistryPort {
        private final List<CredentialRegistryCreate> createCommands = new ArrayList<>();
        @Override public BlockchainReceipt createCredentialState(CredentialRegistryCreate command) {
            recordCreate(command);
            return confirmed("tx-001");
        }
        protected final void recordCreate(CredentialRegistryCreate command) {
            createCommands.add(command);
        }
        List<CredentialRegistryCreate> createCommands() { return List.copyOf(createCommands); }
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
    private static final class LifecycleRegistry extends RecordingRegistry {
        private final List<CredentialRegistryUpdate> updateCommands = new ArrayList<>();
        private final List<CredentialRegistryReissue> reissueCommands = new ArrayList<>();
        @Override public CredentialRegistryState getCredentialState(
            CredentialRegistryReference reference
        ) {
            return new CredentialRegistryState(
                reference.chainKey(), 1, RegistryStatus.ACTIVE, "a".repeat(64), NOW,
                BlockchainProvider.FABRIC_POC);
        }
        @Override public BlockchainReceipt updateCredentialState(CredentialRegistryUpdate command) {
            updateCommands.add(command);
            return confirmed("tx-revoke");
        }
        @Override public BlockchainReceipt reissueCredentialState(CredentialRegistryReissue command) {
            reissueCommands.add(command);
            return confirmed("tx-reissue");
        }
        List<CredentialRegistryUpdate> updateCommands() { return List.copyOf(updateCommands); }
        List<CredentialRegistryReissue> reissueCommands() { return List.copyOf(reissueCommands); }
    }
    private static final class LedgerCommittedLegacyRegistry extends RecordingRegistry {
        private static final String REPLACEMENT_HASH =
            "08f8f312a0e580f60047f484af39f32c76ea9b2da3c5796c7eae8b950c7039a3";
        private final Credential previous;
        private final Credential replacement;
        private final boolean revokeOnly;
        private int updateCalls;
        private int reissueCalls;

        private LedgerCommittedLegacyRegistry(
            Credential previous, Credential replacement, boolean revokeOnly
        ) {
            this.previous = previous;
            this.replacement = replacement;
            this.revokeOnly = revokeOnly;
        }

        @Override public CredentialRegistryState getCredentialState(
            CredentialRegistryReference reference
        ) {
            boolean old = previous.credentialNo().equals(reference.legacyCredentialNo());
            return new CredentialRegistryState(
                reference.chainKey(), 1, old ? RegistryStatus.REVOKED : RegistryStatus.ACTIVE,
                old ? previous.vcHash() : REPLACEMENT_HASH, NOW, BlockchainProvider.FABRIC_POC);
        }

        @Override public BlockchainReceipt updateCredentialState(CredentialRegistryUpdate command) {
            updateCalls++;
            return confirmed("unexpected-update");
        }

        @Override public BlockchainReceipt reissueCredentialState(CredentialRegistryReissue command) {
            reissueCalls++;
            return confirmed("unexpected-reissue");
        }
    }
    private static final class CurrentReissueRegistry extends RecordingRegistry {
        private final String previousKey;
        private final String replacementKey;
        private final List<CredentialRegistryUpdate> supersedeCommands = new ArrayList<>();
        private RegistryStatus previousStatus = RegistryStatus.ACTIVE;
        private boolean replacementExists;
        private int createAttempts;
        private int deleteCalls;

        private CurrentReissueRegistry(String previousKey, String replacementKey) {
            this.previousKey = previousKey;
            this.replacementKey = replacementKey;
        }

        @Override public CredentialRegistryState getCredentialState(CredentialRegistryReference reference) {
            if (previousKey.equals(reference.dataKey())) {
                return new CredentialRegistryState(reference.chainKey(), 1, previousStatus,
                    "a".repeat(64), NOW, BlockchainProvider.FABRIC_POC);
            }
            if (replacementKey.equals(reference.dataKey()) && replacementExists) {
                return new CredentialRegistryState(reference.chainKey(), 1, RegistryStatus.ACTIVE,
                    expectedReplacementHash, NOW, BlockchainProvider.FABRIC_POC);
            }
            throw new BlockchainRegistryException("BLOCKCHAIN_NOT_FOUND");
        }

        private String expectedReplacementHash;

        @Override public BlockchainReceipt updateCredentialState(CredentialRegistryUpdate command) {
            supersedeCommands.add(command);
            previousStatus = RegistryStatus.SUPERSEDED;
            return confirmed("tx-supersede");
        }

        @Override public BlockchainReceipt createCredentialState(CredentialRegistryCreate command) {
            createAttempts++;
            expectedReplacementHash = command.vcHash();
            if (createAttempts == 1) {
                throw new BlockchainRegistryException("BLOCKCHAIN_SUBMIT_FAILED");
            }
            replacementExists = true;
            return confirmed("tx-replacement");
        }
    }
    private static final class FailOnceRegistry extends RecordingRegistry {
        private boolean first = true;
        @Override public BlockchainReceipt createCredentialState(CredentialRegistryCreate command) {
            if (first) {
                first = false;
                recordCreate(command);
                throw new BlockchainRegistryException("BLOCKCHAIN_SUBMIT_FAILED");
            }
            return super.createCredentialState(command);
        }
        /** 제출이 실제로 실패한 경우이므로 원장에는 아무것도 없다. */
        @Override public CredentialRegistryState getCredentialState(
            CredentialRegistryReference reference
        ) {
            throw new BlockchainRegistryException("BLOCKCHAIN_NOT_FOUND");
        }
    }
    /** 원장에 이미 같은 해시로 앵커돼 있어 CreateData 가 계속 거부되는 상황을 재현한다. */
    private static final class AlreadyAnchoredRegistry extends RecordingRegistry {
        @Override public BlockchainReceipt createCredentialState(CredentialRegistryCreate command) {
            recordCreate(command);
            throw new BlockchainRegistryException("BLOCKCHAIN_SUBMIT_FAILED");
        }
        @Override public CredentialRegistryState getCredentialState(
            CredentialRegistryReference reference
        ) {
            return new CredentialRegistryState(
                reference.chainKey(), 1, RegistryStatus.ACTIVE,
                "08f8f312a0e580f60047f484af39f32c76ea9b2da3c5796c7eae8b950c7039a3",
                NOW, BlockchainProvider.FABRIC_POC);
        }
    }
    private static final class UnconfirmedRegistry extends RecordingRegistry {
        @Override public BlockchainReceipt createCredentialState(CredentialRegistryCreate command) {
            return new BlockchainReceipt(
                BlockchainProvider.FABRIC_POC, "tx-invalid", null, null, null, false, "INVALID");
        }
    }

    private static BlockchainReceipt confirmed(String transactionId) {
        return new BlockchainReceipt(
            BlockchainProvider.FABRIC_POC, transactionId, null, null, NOW, true, "VALID");
    }
    private static final class InMemoryCredentialRepository implements CredentialRepository {
        Credential credential; boolean failIssuedUpdate;
        private final java.util.Map<UUID, Credential> byId = new java.util.HashMap<>();
        void seed(Credential value) { credential = value; byId.put(value.id(), value); }
        @Override public Optional<Credential> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
        @Override public Optional<Credential> findByCredentialNo(String credentialNo) { return Optional.empty(); }
        @Override public Optional<Credential> findByCredentialHash(String credentialHash) { return Optional.empty(); }
        @Override public Optional<Credential> findByIdForUpdate(UUID id) { return findById(id); }
        @Override public Optional<Credential> findActiveByGroupId(UUID id) { return Optional.empty(); }
        @Override public List<Credential> findByUserId(UUID id, int l, int o, String s) { return List.of(); }
        @Override public long countByUserId(UUID id) { return 0; }
        @Override public int nextVersionForUpdate(UUID id) { return 1; }
        @Override public void insert(Credential value) { seed(value); }
        @Override public boolean insertIfChainKeyAvailable(Credential value) { insert(value); return true; }
        @Override public void update(Credential value) { if (failIssuedUpdate && value.status() == CredentialStatus.ISSUED) throw new IllegalStateException("db"); seed(value); }
    }
    private static final class InMemoryTransactionRepository implements BlockchainTransactionRepository {
        BlockchainTransaction transaction;
        @Override public List<BlockchainTransaction> claimDue(
            List<String> networks, int limit, Instant now) {
            if (!networks.contains(transaction.network())
                || transaction.status() != BlockchainTransactionStatus.PENDING
                || transaction.nextRetryAt() != null && now.isBefore(transaction.nextRetryAt())) {
                return List.of();
            }
            transaction = transaction.claim(now);
            return List.of(transaction);
        }
        @Override public List<BlockchainTransaction> claimStale(
            List<String> networks, int limit, Instant now, Instant staleBefore) { return List.of(); }
        @Override public Optional<BlockchainTransaction> findById(UUID id) { return Optional.of(transaction); }
        @Override public Optional<BlockchainTransaction> findByIdempotencyKey(String n, String k) { return Optional.of(transaction); }
        @Override public void insert(BlockchainTransaction value) { transaction = value; }
        @Override public void update(BlockchainTransaction value) { transaction = value; }
    }
}
