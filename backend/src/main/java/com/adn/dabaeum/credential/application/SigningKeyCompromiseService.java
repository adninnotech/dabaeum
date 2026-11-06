package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.application.SigningKeyCommands.Compromise;
import com.adn.dabaeum.credential.application.SigningKeyException.Kind;
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
import com.adn.dabaeum.credential.domain.SignKeyStatus;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 키 버전 유출 선언 (설계서 §6.5, §12.2, §15.1). 즉시 동작(버전 행 FOR UPDATE → COMPROMISED, 승인 원자 소비)을
 * 작업 기록과 한 트랜잭션으로 커밋한다. 대상이 현재 버전이고 마스터가 ACTIVE 이며 키 관리자 포트가 있으면 같은
 * 요청에서 COMPROMISE 작업으로 후속 회전을 이어간다. 진행 중 작업이 있어도 즉시 동작은 커밋된다 (긴급 차단).
 * 이 트랜잭션은 tb_credentials 를 잠그지 않는다 (SigningKeyFinalizeCheck 의 잠금 순서).
 * {@code compromise(...)} 는 바깥 트랜잭션(예: {@code @Transactional} 호출자) 안에서 부르면 안 된다 — 긴급 차단
 * 재시도가 첫 트랜잭션이 실제로 롤백되고 새 트랜잭션이 열리는 것을 전제하기 때문이다.
 */
@Service
@Profile({"local", "dev"})
public class SigningKeyCompromiseService {

    private final InstitutionRepository institutions;
    private final InstitutionSignKeyRepository keys;
    private final InstitutionSignKeyVersionRepository versions;
    private final KmsOperationRepository operations;
    private final KmsApprovalRepository approvals;
    private final ApproverRoleCheck approverRoles;
    private final TransitKeyAdminPort keyAdmin;
    private final SigningKeyRotationService rotation;
    private final KmsProcessIdentity identity;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public SigningKeyCompromiseService(
        InstitutionRepository institutions,
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        KmsOperationRepository operations,
        KmsApprovalRepository approvals,
        ApproverRoleCheck approverRoles,
        ObjectProvider<TransitKeyAdminPort> keyAdmin,
        SigningKeyRotationService rotation,
        KmsProcessIdentity identity,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this(institutions, keys, versions, operations, approvals, approverRoles, keyAdmin.getIfAvailable(), rotation,
            identity, transactionManager, clock);
    }

    SigningKeyCompromiseService(
        InstitutionRepository institutions,
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        KmsOperationRepository operations,
        KmsApprovalRepository approvals,
        ApproverRoleCheck approverRoles,
        TransitKeyAdminPort keyAdmin,
        SigningKeyRotationService rotation,
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
        this.rotation = rotation;
        this.identity = identity;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** §6.5. 즉시 동작을 커밋한 뒤, 후속 회전이 있으면 같은 요청에서 이어간다. */
    public KmsOperation compromise(Compromise command) {
        if (institutions.findById(command.institutionId()).isEmpty()) {
            throw new SigningKeyException(Kind.NOT_FOUND, "institution not found");
        }
        Declared declared;
        try {
            declared = declare(command, false);
        } catch (KmsOperationConflictException exception) {
            if (exception.kind() != KmsOperationConflictException.Kind.IN_PROGRESS) {
                throw SigningKeyException.from(exception);
            }
            // §6.5 긴급 차단: 진행 중 작업이 있어도 즉시 동작은 커밋한다. 후속 회전은 그 작업을 판정한 뒤 회전 API 로.
            try {
                declared = declare(command, true);
            } catch (KmsOperationConflictException again) {
                throw SigningKeyException.from(again);
            }
        }
        if (!declared.followUp()) {
            return declared.operation();
        }
        return rotation.continueRotation(declared.master(), declared.operation(), command.requester());
    }

    private record Declared(InstitutionSignKey master, KmsOperation operation, boolean followUp) {
    }

    /**
     * 즉시 동작 한 트랜잭션: 버전 행 FOR UPDATE → 작업 INSERT → 승인자 재확인 → 승인 소비(§12.2) → 버전 COMPROMISED
     * → (후속이면) 소유권 획득. 어느 하나라도 실패하면 전부 롤백. INSERT 의 진행 중 충돌은 호출자가 잡는다.
     */
    private Declared declare(Compromise command, boolean deferFollowUp) {
        Instant now = clock.instant();
        return transactions.execute(status -> {
            InstitutionSignKey master = keys.findCurrent(command.institutionId(), command.purpose())
                .orElseThrow(() -> new SigningKeyException(Kind.NOT_FOUND, "signing key not found"));
            InstitutionSignKeyVersion version = versions
                .findBySignKeyIdAndVersionForUpdate(master.id(), command.keyVersion())
                .orElseThrow(() -> new SigningKeyException(Kind.NOT_FOUND, "key version not found"));
            if (version.status() == SignKeyVersionStatus.COMPROMISED) {
                throw new SigningKeyException(Kind.STATE_CONFLICT, "key version is already compromised");
            }
            String blocker = followUpBlocker(master, version, deferFollowUp);
            boolean followUp = blocker == null;
            KmsOperation operation = followUp
                ? KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.COMPROMISE, command.keyVersion(),
                    command.reason(), command.requester(), command.approvalId(), command.idempotencyKey(), now)
                : KmsOperation.completed(UUID.randomUUID(), master, KmsAction.COMPROMISE, command.keyVersion(),
                    command.reason(), command.requester(), command.approvalId(), command.idempotencyKey(),
                    KmsValues.version(version), KmsValues.version(version.compromise(command.compromisedAt())), now)
                    .annotated(blocker, now);
            operations.insert(operation);
            // 승인 이후 권한을 잃은 승인자의 승인은 소비하지 않는다 (§12.2 권한 재확인).
            approvals.findById(command.approvalId())
                .ifPresent(found -> approverRoles.requirePlatformAdmin(found.approvedBy()));
            boolean consumed = approvals.consume(new KmsApprovalConsumption(command.approvalId(),
                master.institutionId(), master.purpose(), KmsAction.COMPROMISE, command.requester(), master.id(),
                master.currentVersion(), command.keyVersion(), command.compromisedAt(),
                KmsApprovalParams.hash(KmsAction.COMPROMISE, master.purpose(), command.idempotencyKey()),
                operation.id(), now));
            if (!consumed) {
                throw new SigningKeyException(Kind.APPROVAL_REJECTED, "approval does not match this request");
            }
            if (!versions.compromise(version.id(), command.compromisedAt())) {
                throw new SigningKeyException(Kind.STATE_CONFLICT, "key version changed concurrently");
            }
            if (followUp && !operations.acquireOwnership(operation.id(), identity.owner(), now, now.plus(KmsProcessIdentity.LEASE))) {
                throw new SigningKeyException(Kind.STATE_CONFLICT, "operation ownership unavailable", operation.id());
            }
            return new Declared(master, operation, followUp);
        });
    }

    /** 후속 회전을 만들 수 없는 이유. null 이면 만든다 (§6.5: 현재 버전 + ACTIVE 마스터 + 키 관리자 포트). */
    private String followUpBlocker(InstitutionSignKey master, InstitutionSignKeyVersion version,
        boolean deferFollowUp) {
        if (version.keyVersion() != master.currentVersion()) {
            return "follow-up rotation skipped: past version (§6.5)";
        }
        if (deferFollowUp) {
            String inProgress = operations.findInProgressBySignKeyId(master.id())
                .map(operation -> operation.id().toString()).orElse("unknown");
            return "follow-up rotation deferred: operation " + inProgress + " in progress";
        }
        if (master.status() != SignKeyStatus.ACTIVE) {
            return "follow-up rotation skipped: master is " + master.status();
        }
        if (keyAdmin == null) {
            return "follow-up rotation skipped: key management is not configured";
        }
        return null;
    }
}
