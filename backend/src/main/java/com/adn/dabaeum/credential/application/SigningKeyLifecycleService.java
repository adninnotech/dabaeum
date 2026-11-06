package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.application.SigningKeyCommands.CreateApproval;
import com.adn.dabaeum.credential.application.SigningKeyCommands.Resume;
import com.adn.dabaeum.credential.application.SigningKeyCommands.Revoke;
import com.adn.dabaeum.credential.application.SigningKeyCommands.Suspend;
import com.adn.dabaeum.credential.application.SigningKeyException.Kind;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsApproval;
import com.adn.dabaeum.credential.domain.KmsApprovalConsumption;
import com.adn.dabaeum.credential.domain.KmsApprovalParams;
import com.adn.dabaeum.credential.domain.KmsApprovalRepository;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationConflictException;
import com.adn.dabaeum.credential.domain.KmsOperationRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 승인 생성, 정지·재개·폐지, 조회 (설계서 §6.1, §11.5, §12, §14). 정지·재개·폐지는 외부 호출이 없어 작업 기록을
 * COMPLETED 로 한 트랜잭션에 남긴다. 재개·폐지는 같은 트랜잭션에서 승인을 원자 소비한다 (§12.2).
 */
@Service
@Profile({"local", "dev"})
public class SigningKeyLifecycleService {

    static final int OPERATION_HISTORY_LIMIT = 50;

    private final InstitutionRepository institutions;
    private final InstitutionSignKeyRepository keys;
    private final InstitutionSignKeyVersionRepository versions;
    private final KmsOperationRepository operations;
    private final KmsApprovalRepository approvals;
    private final ApproverRoleCheck approverRoles;
    private final CredentialUriProvider uriProvider;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public SigningKeyLifecycleService(
        InstitutionRepository institutions,
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        KmsOperationRepository operations,
        KmsApprovalRepository approvals,
        ApproverRoleCheck approverRoles,
        CredentialUriProvider uriProvider,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this.institutions = institutions;
        this.keys = keys;
        this.versions = versions;
        this.operations = operations;
        this.approvals = approvals;
        this.approverRoles = approverRoles;
        this.uriProvider = uriProvider;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** §12.2. 승인자의 세션으로 만든다. reason 은 해시에 넣지 않는다. */
    public KmsApproval createApproval(CreateApproval command) {
        requireInstitution(command.institutionId());
        if (!KmsApproval.requiresApproval(command.action())) {
            throw new IllegalArgumentException(command.action() + " does not take an approval");
        }
        if ((command.action() == KmsAction.RESUME || command.action() == KmsAction.REVOKE)
            && command.purpose() != SignKeyPurpose.CREDENTIAL) {
            // 정지·재개·폐지는 수료증 마스터만 대상이다 (§6.1). 쓸 수 없는 승인을 만들지 않는다.
            throw new IllegalArgumentException(command.action() + " applies to the credential key only");
        }
        InstitutionSignKey master = currentMaster(command.institutionId(), command.purpose());
        Instant now = clock.instant();
        if (command.action() == KmsAction.COMPROMISE) {
            if (command.keyVersion() == null || command.compromisedAt() == null) {
                throw new IllegalArgumentException("COMPROMISE requires keyVersion and compromisedAt");
            }
            if (command.compromisedAt().isAfter(now)) {
                throw new IllegalArgumentException("compromisedAt must not be in the future");
            }
            InstitutionSignKeyVersion target = versions.findBySignKeyIdAndVersion(master.id(), command.keyVersion())
                .orElseThrow(() -> new SigningKeyException(Kind.NOT_FOUND, "key version not found"));
            // 레거시(버전 0, LOCAL_FILE)의 activatedAt 은 백필 시각일 뿐 실제 키 활성화 시각이 아니므로 하한에서
            // 제외한다 — 그보다 이른 진짜 사고 시각도 선언할 수 있어야 한다 (계획 6 리뷰 라운드 1 판단).
            if (target.source() != InstitutionSignKeyVersion.Source.LOCAL_FILE
                && command.compromisedAt().isBefore(target.activatedAt())) {
                // §6.3: 추정 불가면 activated_at 을 넣는다. 그보다 이른 값은 의미가 없는 오입력이다 (계획 6 결정 12).
                throw new IllegalArgumentException("compromisedAt must not be earlier than the version's activatedAt");
            }
        } else if (command.keyVersion() != null || command.compromisedAt() != null) {
            throw new IllegalArgumentException(command.action() + " must not carry keyVersion or compromisedAt");
        }
        KmsApproval approval = KmsApproval.create(UUID.randomUUID(), master, command.action(),
            command.keyVersion(), command.compromisedAt(),
            KmsApprovalParams.hash(command.action(), command.purpose(), command.idempotencyKey()),
            command.approver(), now);
        transactions.executeWithoutResult(status -> approvals.insert(approval));
        return approval;
    }

    public KmsOperation suspend(Suspend command) {
        requireReason(command.reason());
        return transition(command.institutionId(), KmsAction.SUSPEND, command.requester(), null,
            command.idempotencyKey(), command.reason(),
            (master, now) -> master.suspend(command.reason(), now));
    }

    public KmsOperation resume(Resume command) {
        return transition(command.institutionId(), KmsAction.RESUME, command.requester(), command.approvalId(),
            command.idempotencyKey(), null, (master, now) -> master.resume(now));
    }

    public KmsOperation revoke(Revoke command) {
        requireReason(command.reason());
        return transition(command.institutionId(), KmsAction.REVOKE, command.requester(), command.approvalId(),
            command.idempotencyKey(), command.reason(),
            (master, now) -> master.revoke(command.reason(), now));
    }

    /** §14. 키가 없는 기관은 빈 목록이다. */
    public SigningKeyOverview overview(UUID institutionId) {
        requireInstitution(institutionId);
        Instant now = clock.instant();
        List<SigningKeyOverview.KeyView> views = keys.findByInstitutionId(institutionId).stream()
            .map(master -> new SigningKeyOverview.KeyView(master, versions.findBySignKeyId(master.id()).stream()
                .map(version -> new SigningKeyOverview.VersionView(version,
                    uriProvider.keyId(institutionId, version.kidFragment())))
                .toList()))
            .toList();
        return new SigningKeyOverview(institutionId, views,
            operations.findByInstitutionId(institutionId, OPERATION_HISTORY_LIMIT),
            approvals.findOpenByInstitutionId(institutionId, now));
    }

    /**
     * 수료증 마스터의 상태 전이를 작업 기록(COMPLETED)·승인 소비와 한 트랜잭션에서 한다. 전이 불가는 도메인이
     * IllegalStateException 으로 알리고 STATE_CONFLICT 가 된다.
     */
    private KmsOperation transition(UUID institutionId, KmsAction action, UUID requester, UUID approvalId,
        String idempotencyKey, String reason, BiFunction<InstitutionSignKey, Instant, InstitutionSignKey> change) {
        requireInstitution(institutionId);
        Instant now = clock.instant();
        try {
            return transactions.execute(status -> {
                InstitutionSignKey master = currentMaster(institutionId, SignKeyPurpose.CREDENTIAL);
                InstitutionSignKey changed;
                try {
                    changed = change.apply(master, now);
                } catch (IllegalStateException exception) {
                    throw new SigningKeyException(Kind.STATE_CONFLICT, exception.getMessage());
                }
                KmsOperation operation = KmsOperation.completed(UUID.randomUUID(), master, action, null, reason,
                    requester, approvalId, idempotencyKey, KmsValues.master(master), KmsValues.master(changed), now);
                operations.insert(operation);
                if (approvalId != null) {
                    // 승인 이후 권한을 잃은 승인자의 승인은 소비하지 않는다 (§12.2 권한 재확인).
                    approvals.findById(approvalId)
                        .ifPresent(found -> approverRoles.requirePlatformAdmin(found.approvedBy()));
                    boolean consumed = approvals.consume(new KmsApprovalConsumption(approvalId, institutionId,
                        SignKeyPurpose.CREDENTIAL, action, requester, master.id(), master.currentVersion(), null, null,
                        KmsApprovalParams.hash(action, SignKeyPurpose.CREDENTIAL, idempotencyKey), operation.id(), now));
                    if (!consumed) {
                        throw new SigningKeyException(Kind.APPROVAL_REJECTED, "approval does not match this request");
                    }
                }
                if (!keys.update(changed, master.versionNo())) {
                    throw new SigningKeyException(Kind.STATE_CONFLICT, "master changed concurrently");
                }
                return operation;
            });
        } catch (KmsOperationConflictException exception) {
            throw SigningKeyException.from(exception);
        }
    }

    /** 정지·폐지는 사유가 필수다 (§6.1). 도메인 검사보다 먼저 걸러 상태 충돌과 구분되는 400 으로 알린다. */
    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }

    private void requireInstitution(UUID institutionId) {
        if (institutions.findById(institutionId).isEmpty()) {
            throw new SigningKeyException(Kind.NOT_FOUND, "institution not found");
        }
    }

    private InstitutionSignKey currentMaster(UUID institutionId, SignKeyPurpose purpose) {
        return keys.findCurrent(institutionId, purpose)
            .orElseThrow(() -> new SigningKeyException(Kind.NOT_FOUND, "signing key not found"));
    }
}
