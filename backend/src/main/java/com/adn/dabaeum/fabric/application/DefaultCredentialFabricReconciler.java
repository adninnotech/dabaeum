package com.adn.dabaeum.fabric.application;

import com.adn.dabaeum.blockchain.domain.BlockchainProvider;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryException;
import com.adn.dabaeum.blockchain.domain.BlockchainRegistryPort;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryReference;
import com.adn.dabaeum.blockchain.domain.CredentialRegistryState;
import com.adn.dabaeum.blockchain.domain.RegistryStatus;
import com.adn.dabaeum.blockchain.domain.RegistryTarget;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.fabric.domain.BlockchainTransaction;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionRepository;
import com.adn.dabaeum.fabric.domain.BlockchainTransactionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Provider 중립 읽기 포트로 오래된 처리 중 트랜잭션을 재조정한다. */
@Service
@Profile({"local", "dev"})
public final class DefaultCredentialFabricReconciler implements CredentialFabricReconciler {
    private static final long STALE_SECONDS = 60;
    private static final Set<String> TRANSIENT_READ_CODES = Set.of(
        "BLOCKCHAIN_NOT_FOUND",
        "BLOCKCHAIN_READ_FAILED",
        "BLOCKCHAIN_CONNECTION_FAILED");

    private final BlockchainTransactionRepository transactionRepository;
    private final CredentialRepository credentialRepository;
    private final BlockchainRegistryPort registry;
    private final TransactionTemplate transactions;
    private final RegistryTarget target;

    public DefaultCredentialFabricReconciler(
        BlockchainTransactionRepository transactionRepository,
        CredentialRepository credentialRepository,
        BlockchainRegistryPort registry
    ) {
        this(transactionRepository, credentialRepository, registry, null, RegistryTarget.FABRIC_POC);
    }

    public DefaultCredentialFabricReconciler(
        BlockchainTransactionRepository transactionRepository,
        CredentialRepository credentialRepository,
        BlockchainRegistryPort registry,
        PlatformTransactionManager transactionManager
    ) {
        this(transactionRepository, credentialRepository, registry, transactionManager,
            RegistryTarget.FABRIC_POC);
    }

    @Autowired
    public DefaultCredentialFabricReconciler(
        BlockchainTransactionRepository transactionRepository,
        CredentialRepository credentialRepository,
        BlockchainRegistryPort registry,
        PlatformTransactionManager transactionManager,
        RegistryTarget target
    ) {
        this.transactionRepository = Objects.requireNonNull(transactionRepository);
        this.credentialRepository = Objects.requireNonNull(credentialRepository);
        this.registry = Objects.requireNonNull(registry);
        this.transactions = transactionManager == null
            ? null : new TransactionTemplate(transactionManager);
        this.target = Objects.requireNonNull(target, "target");
    }

    @Override
    public int reconcileBatch(int limit, Instant now) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        Objects.requireNonNull(now, "now");
        var stale = inTransaction(() -> transactionRepository.claimStale(
            target.supportedNetworks(), limit, now, now.minusSeconds(STALE_SECONDS)));
        for (BlockchainTransaction transaction : stale) {
            reconcile(transaction, now);
        }
        return stale.size();
    }

    private void reconcile(BlockchainTransaction transaction, Instant now) {
        try {
            switch (transaction.transactionType()) {
                case VC_ANCHOR -> reconcileIssue(transaction, now);
                case VC_REVOKE -> reconcileRevoke(transaction, now);
                case VC_REISSUE -> reconcileReissue(transaction, now);
            }
        } catch (BlockchainRegistryException exception) {
            if (TRANSIENT_READ_CODES.contains(exception.code())) {
                retry(transaction, exception.code(), now);
            } else {
                fail(transaction, exception.code(), now);
            }
        } catch (RuntimeException exception) {
            fail(transaction, "BLOCKCHAIN_LEDGER_CONFLICT", now);
        }
    }

    private void reconcileIssue(BlockchainTransaction transaction, Instant now) {
        Credential credential = credential(transaction);
        CredentialRegistryState state = registry.getCredentialState(reference(credential));
        if (!matching(state, credential) || state.status() != RegistryStatus.ACTIVE) {
            fail(transaction, "BLOCKCHAIN_LEDGER_CONFLICT", now);
            return;
        }
        inTransaction(() -> {
            Credential current = credentialRepository.findByIdForUpdate(credential.id())
                .orElse(credential);
            if (current.status() == CredentialStatus.ISSUING) {
                requireMaterial(current);
                credentialRepository.update(current.markIssued(
                    current.vcPayload(), current.vcHash(), current.issuerIdentifier(),
                    current.subjectIdentifier(), current.validFrom(), current.issuedAt()));
            } else if (current.status() != CredentialStatus.ISSUED
                || !Objects.equals(current.vcHash(), state.vcHash())) {
                transactionRepository.update(transaction.fail(
                    "BLOCKCHAIN_LEDGER_CONFLICT",
                    "Credential state changed during issue reconciliation", now));
                return null;
            }
            confirm(transaction, reference(credential), now);
            return null;
        });
    }

    private void reconcileRevoke(BlockchainTransaction transaction, Instant now) {
        Credential credential = credential(transaction);
        CredentialRegistryState state = registry.getCredentialState(reference(credential));
        if (!matching(state, credential) || state.status() != RegistryStatus.REVOKED) {
            fail(transaction, "BLOCKCHAIN_LEDGER_CONFLICT", now);
            return;
        }
        inTransaction(() -> {
            Credential current = credentialRepository.findByIdForUpdate(credential.id())
                .orElse(credential);
            if (current.status() == CredentialStatus.ISSUED) {
                credentialRepository.update(current.markRevoked(
                    transaction.operationReason(), transaction.requestedAt()));
            } else if (current.status() != CredentialStatus.REVOKED) {
                transactionRepository.update(transaction.fail(
                    "BLOCKCHAIN_LEDGER_CONFLICT",
                    "Credential state changed during revoke reconciliation", now));
                return null;
            }
            confirm(transaction, reference(credential), now);
            return null;
        });
    }

    private void reconcileReissue(BlockchainTransaction transaction, Instant now) {
        Credential replacement = credential(transaction);
        if (replacement.previousCredentialId() == null) {
            fail(transaction, "BLOCKCHAIN_LEDGER_CONFLICT", now);
            return;
        }
        Credential previous = credentialRepository.findById(replacement.previousCredentialId())
            .orElseThrow(() -> new IllegalStateException("Previous Credential is missing"));
        CredentialRegistryState previousState = registry.getCredentialState(reference(previous));
        CredentialRegistryState replacementState = registry.getCredentialState(reference(replacement));
        RegistryStatus expectedPrevious = previous.chainKey() == null
            ? RegistryStatus.REVOKED : RegistryStatus.SUPERSEDED;
        if (!matching(previousState, previous)
            || previousState.status() != expectedPrevious
            || !matching(replacementState, replacement)
            || replacementState.status() != RegistryStatus.ACTIVE) {
            fail(transaction, "BLOCKCHAIN_LEDGER_CONFLICT", now);
            return;
        }
        inTransaction(() -> {
            Credential old = credentialRepository.findByIdForUpdate(previous.id())
                .orElse(previous);
            Credential current = credentialRepository.findByIdForUpdate(replacement.id())
                .orElse(replacement);
            if (old.status() == CredentialStatus.ISSUED
                && current.status() == CredentialStatus.ISSUING) {
                requireMaterial(current);
                credentialRepository.update(old.markSuperseded(now));
                credentialRepository.update(current.markIssued(
                    current.vcPayload(), current.vcHash(), current.issuerIdentifier(),
                    current.subjectIdentifier(), current.validFrom(), current.issuedAt()));
            } else if (old.status() != CredentialStatus.SUPERSEDED
                || current.status() != CredentialStatus.ISSUED) {
                transactionRepository.update(transaction.fail(
                    "BLOCKCHAIN_LEDGER_CONFLICT",
                    "Credential state changed during reissue reconciliation", now));
                return null;
            }
            confirm(transaction, reference(replacement), now);
            return null;
        });
    }

    private Credential credential(BlockchainTransaction transaction) {
        return credentialRepository.findById(transaction.referenceId())
            .orElseThrow(() -> new IllegalStateException(
                "Credential reconciliation target is missing"));
    }

    private CredentialRegistryReference reference(Credential credential) {
        return target.reference(credential.chainKey(), credential.credentialNo());
    }

    private boolean matching(CredentialRegistryState state, Credential credential) {
        return reference(credential).chainKey().equals(state.chainKey())
            && Objects.equals(credential.vcHash(), state.vcHash());
    }

    private void retry(BlockchainTransaction transaction, String code, Instant now) {
        Instant retryAt = now.plusSeconds(30L * (1L << Math.min(transaction.retryCount(), 4)));
        inTransaction(() -> {
            BlockchainTransaction next = transaction.retry(
                code, "Blockchain read did not prove a commit", retryAt);
            if (next.status() == BlockchainTransactionStatus.FAILED) {
                failIssuingCredential(transaction.referenceId(), code, now);
            }
            transactionRepository.update(next);
            return null;
        });
    }

    private void fail(BlockchainTransaction transaction, String code, Instant now) {
        inTransaction(() -> {
            transactionRepository.update(transaction.fail(
                code, "Blockchain ledger state conflicts", now));
            return null;
        });
    }

    private void failIssuingCredential(UUID credentialId, String code, Instant now) {
        Credential credential = credentialRepository.findByIdForUpdate(credentialId)
            .orElseThrow(() -> new IllegalStateException(
                "Credential reconciliation target is missing"));
        if (credential.status() == CredentialStatus.ISSUING) {
            credentialRepository.update(credential.markFailed(
                code, "Credential blockchain operation failed", now));
        }
    }

    private void confirm(
        BlockchainTransaction transaction, CredentialRegistryReference reference, Instant now
    ) {
        String ledgerTransactionId = LedgerTransactionIds.latest(registry, reference).orElse(null);
        if (ledgerTransactionId == null) {
            transactionRepository.update(transaction.confirm(
                LedgerTransactionIds.unresolved(transaction.id()),
                LedgerTransactionIds.UNRESOLVED_CODE, now));
            return;
        }
        transactionRepository.update(transaction.confirm(
            ledgerTransactionId, LedgerTransactionIds.RECONCILED_CODE, now));
    }

    private void requireMaterial(Credential credential) {
        if (credential.vcPayload() == null || credential.vcHash() == null) {
            throw new IllegalStateException("Credential material is missing");
        }
    }

    private <T> T inTransaction(Supplier<T> operation) {
        return transactions == null
            ? operation.get() : transactions.execute(status -> operation.get());
    }
}
