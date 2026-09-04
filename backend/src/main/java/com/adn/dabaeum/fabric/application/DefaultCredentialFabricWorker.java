package com.adn.dabaeum.fabric.application;

import com.adn.dabaeum.blockchain.domain.BlockchainReceipt;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryException;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryCreate;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReissue;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryState;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryUpdate;
import com.adn.dabaeum.blockchain.domain.LegacyCredentialIssueDetails;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import com.adn.dabaeum.credential.application.CredentialHashService;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialHashVersion;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import com.adn.dabaeum.fabric.domain.BlockchainTransaction;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Provider 중립 원장 제출 전후에 짧은 데이터베이스 단계를 실행한다. */
public final class DefaultCredentialFabricWorker implements CredentialFabricWorker {
    private static final int REGISTRY_SCHEMA_VERSION = 1;

    private final BlockchainTransactionRepository transactionRepository;
    private final CredentialRepository credentialRepository;
    private final CredentialFabricIssuanceContextProvider contextProvider;
    private final CredentialProofService proofService;
    private final CredentialHashService hashService;
    private final BlockchainRegistryPort registry;
    private final TransactionTemplate transactions;
    private final RegistryTarget target;

    /** Fabric POC 대상. 기존 호출부와 테스트를 위해 남긴다. */
    public DefaultCredentialFabricWorker(
        BlockchainTransactionRepository transactionRepository,
        CredentialRepository credentialRepository,
        CredentialFabricIssuanceContextProvider contextProvider,
        CredentialProofService proofService,
        CredentialHashService hashService,
        BlockchainRegistryPort registry,
        PlatformTransactionManager transactionManager
    ) {
        this(transactionRepository, credentialRepository, contextProvider, proofService, hashService,
            registry, transactionManager, RegistryTarget.FABRIC_POC);
    }

    public DefaultCredentialFabricWorker(
        BlockchainTransactionRepository transactionRepository,
        CredentialRepository credentialRepository,
        CredentialFabricIssuanceContextProvider contextProvider,
        CredentialProofService proofService,
        CredentialHashService hashService,
        BlockchainRegistryPort registry,
        PlatformTransactionManager transactionManager,
        RegistryTarget target
    ) {
        this.target = Objects.requireNonNull(target, "target");
        this.transactionRepository = Objects.requireNonNull(
            transactionRepository, "transactionRepository");
        this.credentialRepository = Objects.requireNonNull(
            credentialRepository, "credentialRepository");
        this.contextProvider = Objects.requireNonNull(contextProvider, "contextProvider");
        this.proofService = Objects.requireNonNull(proofService, "proofService");
        this.hashService = Objects.requireNonNull(hashService, "hashService");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(
            transactionManager, "transactionManager"));
    }

    @Override
    public int processBatch(int limit, Instant now) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        Objects.requireNonNull(now, "now");
        var claimed = inTransaction(() -> transactionRepository.claimDue(
            target.supportedNetworks(), limit, now));
        for (BlockchainTransaction transaction : claimed) {
            switch (transaction.transactionType()) {
                case VC_ANCHOR -> processIssue(transaction, now);
                case VC_REVOKE -> processRevoke(transaction, now);
                case VC_REISSUE -> processReissue(transaction, now);
            }
        }
        return claimed.size();
    }

    private void processRevoke(BlockchainTransaction transaction, Instant now) {
        Credential credential = inTransaction(() -> credentialRepository
            .findByIdForUpdate(transaction.referenceId())
            .orElseThrow(() -> new IllegalStateException("Credential revoke target is missing")));
        if (credential.status() != CredentialStatus.ISSUED) {
            inTransaction(() -> {
                transactionRepository.update(transaction.fail("CREDENTIAL_STATE_CONFLICT",
                    "Credential is not issued", now));
                return null;
            });
            return;
        }
        BlockchainReceipt receipt;
        try {
            requireMatchingNetwork(transaction, credential);
            CredentialRegistryState ledger = registry.getCredentialState(reference(credential));
            requireMatchingState(ledger, credential);
            if (ledger.status() == RegistryStatus.REVOKED) {
                receipt = reconciledReceipt(transaction, reference(credential));
            } else if (ledger.status() == RegistryStatus.ACTIVE) {
                receipt = registry.updateCredentialState(new CredentialRegistryUpdate(
                    reference(credential), REGISTRY_SCHEMA_VERSION, RegistryStatus.REVOKED,
                    credential.vcHash(), hashVersion(credential), transaction.requestedAt()));
            } else {
                throw new BlockchainRegistryException("BLOCKCHAIN_LEDGER_CONFLICT");
            }
        } catch (BlockchainRegistryException exception) {
            handleRegistryFailure(credential, transaction, exception, now);
            return;
        }
        if (!receipt.confirmed()) {
            failAfterSubmission(credential, transaction, "BLOCKCHAIN_COMMIT_INVALID", now);
            return;
        }
        inTransaction(() -> {
            Credential current = credentialRepository.findByIdForUpdate(credential.id())
                .orElseThrow(() -> new IllegalStateException("Credential revoke target is missing"));
            if (current.status() == CredentialStatus.ISSUED) {
                credentialRepository.update(current.markRevoked(transaction.operationReason(),
                    transaction.requestedAt()));
            } else if (current.status() != CredentialStatus.REVOKED) {
                transactionRepository.update(transaction.fail("BLOCKCHAIN_LEDGER_CONFLICT",
                    "Credential state changed before revoke commit", now));
                return null;
            }
            transactionRepository.update(transaction.confirm(
                receipt.transactionId(), receipt.resultCode(), receipt.blockHeight(), now));
            return null;
        });
    }

    private void processReissue(BlockchainTransaction transaction, Instant now) {
        PreparedIssue prepared;
        Credential previous;
        try {
            Credential issuing = inTransaction(() -> transitionToIssuing(
                transaction.referenceId(), now));
            if (issuing.previousCredentialId() == null) {
                throw new IllegalStateException("Reissue target has no previous Credential");
            }
            previous = inTransaction(() -> credentialRepository
                .findByIdForUpdate(issuing.previousCredentialId())
                .orElseThrow(() -> new IllegalStateException("Previous Credential is missing")));
            if (previous.status() != CredentialStatus.ISSUED) {
                throw new IllegalStateException("Previous Credential is not issued");
            }
            requireMatchingNetwork(transaction, issuing);
            prepared = prepareIssue(issuing, now);
        } catch (CredentialHashMismatchException exception) {
            failPreparation(transaction, "CREDENTIAL_HASH_MISMATCH", "Credential hash mismatch", now);
            return;
        } catch (RuntimeException exception) {
            failPreparation(transaction, "CREDENTIAL_PROOF_GENERATION_FAILED",
                "Credential proof generation failed", now);
            return;
        }
        BlockchainReceipt receipt;
        try {
            receipt = isLegacy(prepared.credential())
                ? reissueLegacy(previous, prepared, transaction)
                : reissueCurrent(previous, prepared, transaction);
        } catch (BlockchainRegistryException exception) {
            handleRegistryFailure(prepared.credential(), transaction, exception, now);
            return;
        }
        if (!receipt.confirmed()) {
            failAfterSubmission(prepared.credential(), transaction, "BLOCKCHAIN_COMMIT_INVALID", now);
            return;
        }
        inTransaction(() -> {
            Credential old = credentialRepository.findByIdForUpdate(previous.id())
                .orElseThrow(() -> new IllegalStateException("Previous Credential is missing"));
            Credential current = credentialRepository.findByIdForUpdate(prepared.credential().id())
                .orElseThrow(() -> new IllegalStateException("Reissue target is missing"));
            if (old.status() == CredentialStatus.ISSUED
                && current.status() == CredentialStatus.ISSUING) {
                credentialRepository.update(old.markSuperseded(now));
                credentialRepository.update(current.markIssued(prepared.payload(), prepared.vcHash(),
                    prepared.context().issuerIdentifier(), prepared.context().subjectIdentifier(),
                    prepared.context().issuedAt(), prepared.context().issuedAt()));
            } else if (old.status() != CredentialStatus.SUPERSEDED
                || current.status() != CredentialStatus.ISSUED) {
                transactionRepository.update(transaction.fail("BLOCKCHAIN_LEDGER_CONFLICT",
                    "Credential state changed before reissue commit", now));
                return null;
            }
            transactionRepository.update(transaction.confirm(
                receipt.transactionId(), receipt.resultCode(), receipt.blockHeight(), now));
            return null;
        });
    }

    /** 레거시 원자적 재발급은 모호한 commit 결과를 먼저 조회한 뒤에만 재제출한다. */
    private BlockchainReceipt reissueLegacy(
        Credential previous,
        PreparedIssue prepared,
        BlockchainTransaction transaction
    ) {
        CredentialRegistryState previousState = registry.getCredentialState(reference(previous));
        requireMatchingState(previousState, previous);
        if (previousState.status() == RegistryStatus.REVOKED) {
            CredentialRegistryState replacementState = registry.getCredentialState(
                reference(prepared.credential()));
            requireMatchingState(replacementState, prepared.credential());
            if (replacementState.status() != RegistryStatus.ACTIVE) {
                throw new BlockchainRegistryException("BLOCKCHAIN_LEDGER_CONFLICT");
            }
            return reconciledReceipt(transaction, reference(prepared.credential()));
        }
        if (previousState.status() != RegistryStatus.ACTIVE) {
            throw new BlockchainRegistryException("BLOCKCHAIN_LEDGER_CONFLICT");
        }
        return registry.reissueCredentialState(new CredentialRegistryReissue(
            reference(previous), reference(prepared.credential()), REGISTRY_SCHEMA_VERSION,
            prepared.vcHash(), hashVersion(prepared.credential()),
            prepared.context().issuedAt(), prepared.legacyDetails(), transaction.requestedAt()));
    }

    /** 신규 Storage 재발급의 두 원장 쓰기를 상태 조회로 재개 가능하게 연결한다. */
    private BlockchainReceipt reissueCurrent(
        Credential previous,
        PreparedIssue prepared,
        BlockchainTransaction transaction
    ) {
        CredentialRegistryReference previousReference = reference(previous);
        CredentialRegistryReference replacementReference = reference(prepared.credential());
        CredentialRegistryState previousState = registry.getCredentialState(previousReference);
        if (!previous.vcHash().equals(previousState.vcHash())) {
            throw new BlockchainRegistryException("BLOCKCHAIN_LEDGER_CONFLICT");
        }

        if (previousState.status() == RegistryStatus.ACTIVE) {
            BlockchainReceipt updateReceipt = registry.updateCredentialState(new CredentialRegistryUpdate(
                previousReference, REGISTRY_SCHEMA_VERSION, RegistryStatus.SUPERSEDED,
                previous.vcHash(), hashVersion(previous), transaction.requestedAt()));
            requireConfirmed(updateReceipt);
        } else if (previousState.status() != RegistryStatus.SUPERSEDED) {
            throw new BlockchainRegistryException("BLOCKCHAIN_LEDGER_CONFLICT");
        }

        try {
            BlockchainReceipt createReceipt = registry.createCredentialState(
                new CredentialRegistryCreate(
                    replacementReference, REGISTRY_SCHEMA_VERSION, RegistryStatus.ACTIVE,
                    prepared.vcHash(), hashVersion(prepared.credential()),
                    prepared.context().issuedAt(), null));
            requireConfirmed(createReceipt);
            return createReceipt;
        } catch (BlockchainRegistryException createFailure) {
            CredentialRegistryState replacementState = existingLedgerState(
                replacementReference, createFailure);
            if (replacementState.status() != RegistryStatus.ACTIVE
                || !prepared.vcHash().equals(replacementState.vcHash())) {
                throw new BlockchainRegistryException("BLOCKCHAIN_LEDGER_CONFLICT");
            }
            return reconciledReceipt(transaction, replacementReference);
        }
    }

    /**
     * 앵커 제출이 실패해도 원장에는 이미 반영돼 있을 수 있다. commit 확인이 타임아웃된 뒤의
     * 재시도가 대표적이며, 그때 CreateData 는 계속 "already exists" 로 거부된다. 이를 그대로
     * 실패로 처리하면 재시도를 모두 소진하고 Credential 이 FAILED 가 되어, 원장은 ACTIVE 인데
     * DB 는 실패인 상태가 영구히 남는다. Reconciler 는 PROCESSING 만 회수하므로 이 건을
     * 복구하지 못한다.
     *
     * <p>같은 해시로 이미 ACTIVE 이면 성공으로 간주한다. reissueCurrent 가 쓰는 복구 방식과
     * 같으며, 발급 경로에만 빠져 있었다.
     */
    private BlockchainReceipt createOrReconcileAnchor(
        PreparedIssue prepared,
        BlockchainTransaction transaction
    ) {
        CredentialRegistryReference reference = reference(prepared.credential());
        try {
            return registry.createCredentialState(new CredentialRegistryCreate(
                reference, REGISTRY_SCHEMA_VERSION, RegistryStatus.ACTIVE,
                prepared.vcHash(), hashVersion(prepared.credential()),
                prepared.context().issuedAt(), prepared.legacyDetails()));
        } catch (BlockchainRegistryException createFailure) {
            CredentialRegistryState existing = existingLedgerState(reference, createFailure);
            if (existing.status() != RegistryStatus.ACTIVE
                || !prepared.vcHash().equals(existing.vcHash())) {
                throw new BlockchainRegistryException("BLOCKCHAIN_LEDGER_CONFLICT");
            }
            return reconciledReceipt(transaction, reference);
        }
    }

    private CredentialRegistryState existingLedgerState(
        CredentialRegistryReference reference,
        BlockchainRegistryException createFailure
    ) {
        try {
            return registry.getCredentialState(reference);
        } catch (BlockchainRegistryException readFailure) {
            throw createFailure;
        }
    }

    private void requireConfirmed(BlockchainReceipt receipt) {
        if (!receipt.confirmed()) {
            throw new BlockchainRegistryException("BLOCKCHAIN_COMMIT_INVALID");
        }
    }

    private void requireMatchingState(CredentialRegistryState state, Credential credential) {
        if (!reference(credential).chainKey().equals(state.chainKey())
            || !credential.vcHash().equals(state.vcHash())) {
            throw new BlockchainRegistryException("BLOCKCHAIN_LEDGER_CONFLICT");
        }
    }

    /**
     * 원장에 이미 반영된 건을 DB 와 맞출 때의 영수증. 이력에서 실제 트랜잭션 ID 를 찾아 쓰고,
     * 이력을 읽을 수 없을 때만 합성 ID 로 표시하되 commitCode 로 구분해 모니터링이 가짜 ID 를
     * 진짜처럼 보여주지 않게 한다.
     */
    private BlockchainReceipt reconciledReceipt(
        BlockchainTransaction transaction, CredentialRegistryReference reference
    ) {
        String ledgerTransactionId = LedgerTransactionIds.latest(registry, reference).orElse(null);
        if (ledgerTransactionId == null) {
            return new BlockchainReceipt(
                target.provider(),
                LedgerTransactionIds.unresolved(transaction.id()), null, null, null, true,
                LedgerTransactionIds.UNRESOLVED_CODE);
        }
        return new BlockchainReceipt(
            target.provider(),
            ledgerTransactionId, null, null, null, true, LedgerTransactionIds.RECONCILED_CODE);
    }

    private void processIssue(BlockchainTransaction transaction, Instant now) {
        PreparedIssue prepared;
        try {
            Credential issuing = inTransaction(() -> transitionToIssuing(
                transaction.referenceId(), now));
            requireMatchingNetwork(transaction, issuing);
            prepared = prepareIssue(issuing, now);
        } catch (CredentialHashMismatchException exception) {
            failPreparation(transaction, "CREDENTIAL_HASH_MISMATCH", "Credential hash mismatch", now);
            return;
        } catch (RuntimeException exception) {
            failPreparation(transaction, "CREDENTIAL_PROOF_GENERATION_FAILED",
                "Credential proof generation failed", now);
            return;
        }

        BlockchainReceipt receipt;
        try {
            receipt = createOrReconcileAnchor(prepared, transaction);
        } catch (BlockchainRegistryException exception) {
            handleRegistryFailure(prepared.credential(), transaction, exception, now);
            return;
        }
        if (!receipt.confirmed()) {
            failAfterSubmission(prepared.credential(), transaction, "BLOCKCHAIN_COMMIT_INVALID", now);
            return;
        }
        inTransaction(() -> {
            Credential current = credentialRepository.findByIdForUpdate(prepared.credential().id())
                .orElseThrow(() -> new IllegalStateException("Credential anchor target is missing"));
            if (current.status() == CredentialStatus.ISSUING) {
                credentialRepository.update(current.markIssued(
                    prepared.payload(), prepared.vcHash(),
                    prepared.context().issuerIdentifier(), prepared.context().subjectIdentifier(),
                    prepared.context().issuedAt(), prepared.context().issuedAt()));
            } else if (current.status() != CredentialStatus.ISSUED
                || !Objects.equals(current.vcHash(), prepared.vcHash())) {
                transactionRepository.update(transaction.fail("BLOCKCHAIN_LEDGER_CONFLICT",
                    "Credential state changed before issue commit", now));
                return null;
            }
            transactionRepository.update(transaction.confirm(
                receipt.transactionId(), receipt.resultCode(), receipt.blockHeight(), now));
            return null;
        });
    }

    private Credential transitionToIssuing(java.util.UUID credentialId, Instant now) {
        return inTransaction(() -> {
            Credential credential = credentialRepository.findByIdForUpdate(credentialId)
                .orElseThrow(() -> new IllegalStateException("Credential anchor target is missing"));
            Credential issuing = credential.status() == CredentialStatus.PENDING
                ? credential.startIssuing(now) : credential;
            if (issuing != credential) credentialRepository.update(issuing);
            return issuing;
        });
    }

    private PreparedIssue prepareIssue(Credential issuing, Instant now) {
        CredentialFabricIssuanceContext context;
        SignedCredentialEnvelope envelope;
        if (issuing.vcPayload() != null && issuing.vcHash() != null
            && issuing.issuerIdentifier() != null && issuing.subjectIdentifier() != null
            && issuing.issuedAt() != null) {
            context = new CredentialFabricIssuanceContext(
                new com.adn.dabaeum.credential.domain.CredentialDocument("{}"),
                issuing.issuerIdentifier(), issuing.subjectIdentifier(), issuing.issuedAt());
            envelope = existingOrSign(issuing);
        } else {
            context = contextProvider.resolve(issuing.id(), now);
            envelope = proofService.sign(context.document());
        }

        String vcHash = isLegacy(issuing)
            ? hashService.sha256(envelope)
            : hashService.sha256CompactJws(envelope.compactJws());
        String payload = hashService.serialize(envelope);
        if (issuing.vcHash() != null && !issuing.vcHash().equals(vcHash)) {
            throw new CredentialHashMismatchException();
        }
        if (issuing.vcPayload() == null) {
            issuing = persistEnvelope(issuing, context, payload, vcHash, now);
        }
        LegacyCredentialIssueDetails legacyDetails = isLegacy(issuing)
            ? new LegacyCredentialIssueDetails(
                prefixedHash(context.subjectIdentifier()),
                prefixedHash(context.issuerIdentifier()),
                "LIFELONG_EDUCATION_COMPLETION", context.issuedAt())
            : null;
        return new PreparedIssue(issuing, context, payload, vcHash, legacyDetails);
    }

    private Credential persistEnvelope(
        Credential issuing,
        CredentialFabricIssuanceContext context,
        String payload,
        String vcHash,
        Instant now
    ) {
        return inTransaction(() -> {
            Credential current = credentialRepository.findByIdForUpdate(issuing.id())
                .orElseThrow(() -> new IllegalStateException("Credential anchor target is missing"));
            if (current.vcPayload() != null && current.vcHash() != null) {
                return current;
            }
            Credential saved = new Credential(
                current.id(), current.credentialGroupId(), current.previousCredentialId(),
                current.credentialNo(), current.versionNo(), context.issuerIdentifier(),
                context.subjectIdentifier(), current.credentialType(), CredentialStatus.ISSUING,
                context.issuedAt(), current.validUntil(), payload, vcHash, current.chainKey(),
                current.vcHashVersion(), context.issuedAt(), null, null, null, null,
                current.createdAt(), now);
            credentialRepository.update(saved);
            return saved;
        });
    }

    private void failPreparation(
        BlockchainTransaction transaction,
        String code,
        String message,
        Instant now
    ) {
        inTransaction(() -> {
            Credential credential = credentialRepository.findByIdForUpdate(transaction.referenceId())
                .orElseThrow(() -> new IllegalStateException("Credential anchor target is missing"));
            Credential issuing = credential.status() == CredentialStatus.PENDING
                ? credential.startIssuing(now) : credential;
            markCredentialFailed(issuing, code, now);
            transactionRepository.update(transaction.fail(code, message, now));
            return null;
        });
    }

    private void failAfterSubmission(
        Credential credential,
        BlockchainTransaction transaction,
        String code,
        Instant now
    ) {
        inTransaction(() -> {
            Credential current = credentialRepository.findByIdForUpdate(credential.id())
                .orElse(credential);
            markCredentialFailed(current, code, now);
            transactionRepository.update(transaction.fail(
                code, "Blockchain commit was not confirmed", now));
            return null;
        });
    }

    private SignedCredentialEnvelope existingOrSign(Credential credential) {
        if (credential.vcPayload() != null && credential.vcHash() != null) {
            try {
                tools.jackson.databind.JsonNode node = new tools.jackson.databind.ObjectMapper()
                    .readTree(credential.vcPayload());
                return new SignedCredentialEnvelope(
                    node.get("mediaType").asString(), node.get("compactJws").asString());
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Stored credential envelope is invalid");
            }
        }
        throw new IllegalStateException("Credential envelope is missing");
    }

    private void handleRegistryFailure(
        Credential credential,
        BlockchainTransaction transaction,
        BlockchainRegistryException exception,
        Instant now
    ) {
        String code = exception.code();
        Instant retryAt = now.plusSeconds(30L * (1L << Math.min(transaction.retryCount(), 4)));
        BlockchainTransaction next = transaction.retry(
            code, "Blockchain submission failed", retryAt);
        inTransaction(() -> {
            if (next.status() == BlockchainTransactionStatus.FAILED) {
                Credential current = credentialRepository.findByIdForUpdate(credential.id())
                    .orElse(credential);
                markCredentialFailed(current, code, now);
            }
            transactionRepository.update(next);
            return null;
        });
    }

    private void markCredentialFailed(Credential credential, String code, Instant now) {
        if (credential.status() == CredentialStatus.ISSUING) {
            credentialRepository.update(credential.markFailed(
                code, "Credential issuance failed", now));
        }
    }

    private CredentialRegistryReference reference(Credential credential) {
        return target.reference(credential.chainKey(), credential.credentialNo());
    }

    private String hashVersion(Credential credential) {
        return isLegacy(credential)
            ? CredentialHashVersion.ENVELOPE_SHA256_V0.name()
            : CredentialHashVersion.COMPACT_JWS_SHA256_V1.name();
    }

    private boolean isLegacy(Credential credential) {
        return credential.chainKey() == null;
    }

    private void requireMatchingNetwork(
        BlockchainTransaction transaction,
        Credential credential
    ) {
        if (!target.accepts(transaction.network(), isLegacy(credential))) {
            throw new IllegalStateException("Credential registry network does not match metadata");
        }
    }

    private String prefixedHash(String value) {
        return "sha256:" + HexFormat.of().formatHex(sha256(value));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private <T> T inTransaction(Supplier<T> operation) {
        return transactions.execute(status -> operation.get());
    }

    private record PreparedIssue(
        Credential credential,
        CredentialFabricIssuanceContext context,
        String payload,
        String vcHash,
        LegacyCredentialIssueDetails legacyDetails
    ) { }

    private static final class CredentialHashMismatchException extends IllegalStateException {
        private CredentialHashMismatchException() {
            super("Credential hash mismatch");
        }
    }
}
