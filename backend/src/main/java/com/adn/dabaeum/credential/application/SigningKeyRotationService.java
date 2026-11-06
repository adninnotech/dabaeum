package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.application.SigningKeyCommands.Rotate;
import com.adn.dabaeum.credential.application.SigningKeyException.Kind;
import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsApprovalConsumption;
import com.adn.dabaeum.credential.domain.KmsApprovalParams;
import com.adn.dabaeum.credential.domain.KmsApprovalRepository;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationConflictException;
import com.adn.dabaeum.credential.domain.KmsOperationRepository;
import com.adn.dabaeum.credential.domain.KmsOperationStatus;
import com.adn.dabaeum.credential.domain.SignKeyStatus;
import com.adn.dabaeum.credential.domain.TransitKeyAdminException;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import com.adn.dabaeum.credential.domain.TransitKeyInfo;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 키 회전 (설계서 §6.2 T1~T3, §12.2, §15.1). 승인 소비·작업 기록·소유권을 T1 에서 커밋한 뒤 rotate 를 부르고,
 * 재조회로 n+1 을 확인한 것만 T2 로 반영한다. 결과가 불확실하면 재호출하지 않고 UNCONFIRMED 로 남긴다.
 */
@Service
@Profile({"local", "dev"})
public class SigningKeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyRotationService.class);

    private final InstitutionRepository institutions;
    private final InstitutionSignKeyRepository keys;
    private final InstitutionSignKeyVersionRepository versions;
    private final KmsOperationRepository operations;
    private final KmsApprovalRepository approvals;
    private final ApproverRoleCheck approverRoles;
    private final TransitKeyAdminPort keyAdmin;
    private final CredentialPublicJwkFactory jwks;
    private final KmsProcessIdentity identity;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public SigningKeyRotationService(
        InstitutionRepository institutions,
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        KmsOperationRepository operations,
        KmsApprovalRepository approvals,
        ApproverRoleCheck approverRoles,
        ObjectProvider<TransitKeyAdminPort> keyAdmin,
        CredentialPublicJwkFactory jwks,
        KmsProcessIdentity identity,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this(institutions, keys, versions, operations, approvals, approverRoles, keyAdmin.getIfAvailable(), jwks,
            identity, transactionManager, clock);
    }

    SigningKeyRotationService(
        InstitutionRepository institutions,
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        KmsOperationRepository operations,
        KmsApprovalRepository approvals,
        ApproverRoleCheck approverRoles,
        TransitKeyAdminPort keyAdmin,
        CredentialPublicJwkFactory jwks,
        KmsProcessIdentity identity,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this.institutions = institutions;
        this.keys = keys;
        this.versions = versions;
        this.operations = operations;
        this.approvals = approvals;
        this.approverRoles = approverRoles;
        this.keyAdmin = keyAdmin;
        this.jwks = jwks;
        this.identity = identity;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public KmsOperation rotate(Rotate command) {
        if (institutions.findById(command.institutionId()).isEmpty()) {
            throw new SigningKeyException(Kind.NOT_FOUND, "institution not found");
        }
        requireKeyAdmin();
        Prepared prepared = prepare(command);
        return continueRotation(prepared.master(), prepared.operation(), command.requester());
    }

    /**
     * T1 이후 절차: 소유권 재확인 → rotate → 재조회 → T2 → min_encryption_version → T3 (§6.2). 유출 선언의 후속
     * 회전(§6.5)이 자기 T1 뒤에 그대로 부른다. operation 은 PREPARED 이고 이 프로세스가 소유권을 쥐고 있어야 한다.
     */
    public KmsOperation continueRotation(InstitutionSignKey master, KmsOperation operation, UUID requester) {
        requireKeyAdmin();
        int next = master.currentVersion() + 1;

        reconfirm(operation, KmsOperationStatus.PREPARED);
        try {
            keyAdmin.rotateKey(master.mount(), master.keyName());
        } catch (TransitKeyAdminException exception) {
            if (exception.reason() == TransitKeyAdminException.Reason.REJECTED) {
                KmsOperation failed = persist(operation.unconfirmed(now()).failed("openbao rejected rotate", now()));
                release(failed);
                throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "openbao rejected rotate", failed.id());
            }
            persist(operation.unconfirmed(now()));
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "rotation result is unconfirmed", operation.id());
        }

        TransitKeyInfo info;
        try {
            info = read(master);
        } catch (TransitKeyAdminException exception) {
            persist(operation.unconfirmed(now()));
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "rotation result could not be read", operation.id());
        }
        if (info.latestVersion() != next) {
            // 귀속을 확정할 수 없다. 관측 버전만 남기고 운영자 판정에 맡긴다 (§15.1).
            persist(operation.unconfirmed(now()).observed(info.latestVersion(), now()));
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE,
                "observed key version does not match the expected rotation", operation.id());
        }

        KmsOperation recorded = record(operation, master, info, next, requester);
        configure(recorded, master, info, next);
        return complete(recorded);
    }

    /**
     * 운영자 판정 RECORDED (§15.1). PREPARED·UNCONFIRMED 는 재조회로 latest_version == expected + 1 을 확인한 뒤
     * T2·config·T3 를, RECORDED 는 config·T3 만 이어간다. rotate 를 다시 부르지 않는다. 호출자(판정 서비스)가 이
     * 프로세스로 소유권을 쥐고 있어야 한다. 재조회가 실패하거나 버전이 어긋나면 상태를 바꾸지 않는다(관측만 남긴다).
     */
    public KmsOperation resume(InstitutionSignKey master, KmsOperation operation, UUID requester) {
        requireKeyAdmin();
        int next = operation.expectedVersionBefore() + 1;
        TransitKeyInfo info;
        try {
            info = read(master);
        } catch (TransitKeyAdminException exception) {
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "key could not be read; nothing changed",
                operation.id());
        }
        if (operation.status() == KmsOperationStatus.RECORDED) {
            configure(operation, master, info, next);
            return complete(operation);
        }
        if (info.latestVersion() != next) {
            persist(operation.observed(info.latestVersion(), now()));
            throw new SigningKeyException(Kind.STATE_CONFLICT, "observed key version " + info.latestVersion()
                + " does not match expected " + next + "; the operation stays " + operation.status(), operation.id());
        }
        KmsOperation recorded = record(operation, master, info, next, requester);
        configure(recorded, master, info, next);
        return complete(recorded);
    }

    private void requireKeyAdmin() {
        if (keyAdmin == null) {
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "key management is not configured");
        }
    }

    /** GET …/keys/{name}. 없으면 REJECTED 로 취급한다 (키가 사라짐). */
    private TransitKeyInfo read(InstitutionSignKey master) {
        return keyAdmin.readKey(master.mount(), master.keyName())
            .orElseThrow(() -> new TransitKeyAdminException(TransitKeyAdminException.Reason.REJECTED, "key vanished",
                null));
    }

    /**
     * 작업을 UNCONFIRMED·관측 버전으로 남긴다. 이미 UNCONFIRMED 인 작업(운영자 재개)은 다시 전이시키지 않는다.
     */
    private void unconfirm(KmsOperation operation, TransitKeyInfo info, Instant now) {
        KmsOperation base = operation.status() == KmsOperationStatus.PREPARED
            ? operation.unconfirmed(now)
            : operation;
        persist(base.observed(info.latestVersion(), now));
    }

    private record Prepared(InstitutionSignKey master, KmsOperation operation) {
    }

    /** T1: 마스터 확인 → 작업 PREPARED INSERT → 승인 소비 → 소유권 획득. 하나라도 실패하면 롤백. */
    private Prepared prepare(Rotate command) {
        Instant now = now();
        try {
            return transactions.execute(status -> {
                InstitutionSignKey master = keys.findCurrent(command.institutionId(), command.purpose())
                    .orElseThrow(() -> new SigningKeyException(Kind.NOT_FOUND, "signing key not found"));
                if (master.status() != SignKeyStatus.ACTIVE) {
                    throw new SigningKeyException(Kind.STATE_CONFLICT,
                        "only an ACTIVE key can be rotated (status " + master.status() + ")");
                }
                KmsOperation operation = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null,
                    null, command.requester(), command.approvalId(), command.idempotencyKey(), now);
                operations.insert(operation);
                // 승인 이후 권한을 잃은 승인자의 승인은 소비하지 않는다 (§12.2 권한 재확인).
                approvals.findById(command.approvalId())
                    .ifPresent(found -> approverRoles.requirePlatformAdmin(found.approvedBy()));
                boolean consumed = approvals.consume(new KmsApprovalConsumption(command.approvalId(),
                    master.institutionId(), master.purpose(), KmsAction.ROTATE, command.requester(), master.id(),
                    master.currentVersion(), null, null,
                    KmsApprovalParams.hash(KmsAction.ROTATE, master.purpose(), command.idempotencyKey()),
                    operation.id(), now));
                if (!consumed) {
                    throw new SigningKeyException(Kind.APPROVAL_REJECTED, "approval does not match this request");
                }
                if (!operations.acquireOwnership(operation.id(), identity.owner(), now, now.plus(KmsProcessIdentity.LEASE))) {
                    throw new SigningKeyException(Kind.STATE_CONFLICT, "operation ownership unavailable",
                        operation.id());
                }
                return new Prepared(master, operation);
            });
        } catch (KmsOperationConflictException exception) {
            throw SigningKeyException.from(exception);
        }
    }

    /** T2: 마스터 n+1 → 버전 n 조건부 RETIRED → 버전 n+1 ACTIVE → 작업 RECORDED. 이 커밋이 공개 시점이다. */
    private KmsOperation record(KmsOperation operation, InstitutionSignKey master, TransitKeyInfo info, int next,
        UUID requester) {
        Instant now = now();
        TransitKeyInfo.Version version = info.version(next);
        try {
            return transactions.execute(status -> {
                InstitutionSignKey current = keys.findById(master.id()).orElseThrow(
                    () -> new SigningKeyException(Kind.STATE_CONFLICT, "master disappeared", operation.id()));
                if (current.status() != SignKeyStatus.ACTIVE || current.currentVersion() != next - 1) {
                    // current.rotated(next, now) 는 이 두 조건을 IllegalStateException 으로만 지킨다.
                    // 여기서 먼저 걸러 SigningKeyException 으로 통일한다(§6.2 T2, 동시 변경).
                    throw new SigningKeyException(Kind.STATE_CONFLICT, "master changed concurrently", operation.id());
                }
                InstitutionSignKey rotated = current.rotated(next, now);
                if (!keys.update(rotated, current.versionNo())) {
                    throw new SigningKeyException(Kind.STATE_CONFLICT, "master changed concurrently", operation.id());
                }
                versions.findBySignKeyIdAndVersion(master.id(), next - 1)
                    .ifPresent(previous -> versions.retireIfActive(previous.id(), now));
                versions.insert(InstitutionSignKeyVersion.active(UUID.randomUUID(), rotated.id(),
                    rotated.institutionId(), rotated.purpose(), rotated.generation(), next,
                    KmsValues.storedJwk(jwks, version.publicKey()), version.creationTime(), requester, now));
                KmsOperation recorded = operation.recorded(next, KmsValues.master(rotated), now);
                updateOperation(recorded);
                return recorded;
            });
        } catch (SigningKeyException exception) {
            unconfirm(operation, info, now);
            throw exception;
        } catch (RuntimeException exception) {
            // 예상치 못한 예외(예: current.rotated 의 IllegalStateException)까지 원장에 새고 나가면
            // 작업이 PREPARED 로 남아 OpenBao 는 이미 n+1 인데 DB 는 회전을 모르는 상태가 된다.
            unconfirm(operation, info, now);
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE,
                "rotation recorded in OpenBao but not in the database", operation.id());
        }
    }

    /** min_encryption_version = max(현재, n+1). 실패하면 재확인 뒤 한 번만 더 (§6.2). */
    private void configure(KmsOperation recorded, InstitutionSignKey master, TransitKeyInfo info, int next) {
        int target = Math.max(info.minEncryptionVersion(), next);
        for (int attempt = 1; attempt <= 2; attempt++) {
            reconfirm(recorded, KmsOperationStatus.RECORDED);
            try {
                keyAdmin.setMinEncryptionVersion(master.mount(), master.keyName(), target);
                return;
            } catch (TransitKeyAdminException exception) {
                log.warn("min_encryption_version attempt {} failed for {}/{}: {}", attempt, master.mount(),
                    master.keyName(), exception.reason(), exception);
                if (attempt == 2) {
                    throw new SigningKeyException(Kind.KMS_UNAVAILABLE,
                        "min_encryption_version was not applied; operation stays RECORDED", recorded.id());
                }
            }
        }
    }

    /** T3. */
    private KmsOperation complete(KmsOperation recorded) {
        Instant now = now();
        return transactions.execute(status -> {
            KmsOperation completed = recorded.completed(now);
            updateOperation(completed);
            if (!operations.releaseOwnership(completed.id(), identity.owner())) {
                log.warn("failed to release ownership for kms operation {}", completed.id());
            }
            return completed;
        });
    }

    private void reconfirm(KmsOperation operation, KmsOperationStatus expected) {
        Instant now = now();
        if (!operations.reconfirmOwnership(operation.id(), identity.owner(), expected, now, now.plus(KmsProcessIdentity.LEASE))) {
            throw new SigningKeyException(Kind.STATE_CONFLICT, "operation ownership lost", operation.id());
        }
    }

    private KmsOperation persist(KmsOperation operation) {
        transactions.executeWithoutResult(status -> updateOperation(operation));
        return operation;
    }

    /** 0행은 작업 기록이 사라진 것이다. 흐름은 그대로 두고 경고만 남긴다. */
    private void updateOperation(KmsOperation operation) {
        if (!operations.update(operation)) {
            log.warn("kms operation {} was not updated: 0 rows", operation.id());
        }
    }

    private void release(KmsOperation operation) {
        transactions.executeWithoutResult(status -> {
            if (!operations.releaseOwnership(operation.id(), identity.owner())) {
                log.warn("failed to release ownership for kms operation {}", operation.id());
            }
        });
    }

    private Instant now() {
        return clock.instant();
    }
}
