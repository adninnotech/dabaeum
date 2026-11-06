package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.application.SigningKeyCommands.Adjudicate;
import com.adn.dabaeum.credential.application.SigningKeyCommands.Adjudicate.Decision;
import com.adn.dabaeum.credential.application.SigningKeyException.Kind;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationRepository;
import com.adn.dabaeum.credential.domain.KmsOperationStatus;
import com.adn.dabaeum.credential.domain.SignKeyStatus;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영자 판정 (설계서 §15.1). 복구 워커는 판정하지 않으므로 PREPARED·UNCONFIRMED·RECORDED 로 남은 작업은 여기서만
 * 끝난다. FAILED 는 작업(과 CREATE 의 PROVISIONING 마스터)을 한 트랜잭션에서 닫고, RECORDED 는 회전·생성 서비스의
 * resume 으로 T2 이후를 이어간다. 근거·판정자·시각은 작업 reason 에 덧붙이고 구조화 로그에 남긴다.
 */
@Service
@Profile({"local", "dev"})
public class KmsOperationAdjudicationService {

    private static final Logger log = LoggerFactory.getLogger(KmsOperationAdjudicationService.class);

    private final InstitutionRepository institutions;
    private final InstitutionSignKeyRepository keys;
    private final KmsOperationRepository operations;
    private final SigningKeyProvisioningService provisioning;
    private final SigningKeyRotationService rotation;
    private final KmsProcessIdentity identity;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public KmsOperationAdjudicationService(
        InstitutionRepository institutions,
        InstitutionSignKeyRepository keys,
        KmsOperationRepository operations,
        SigningKeyProvisioningService provisioning,
        SigningKeyRotationService rotation,
        KmsProcessIdentity identity,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this.institutions = institutions;
        this.keys = keys;
        this.operations = operations;
        this.provisioning = provisioning;
        this.rotation = rotation;
        this.identity = identity;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public KmsOperation adjudicate(Adjudicate command) {
        if (institutions.findById(command.institutionId()).isEmpty()) {
            throw new SigningKeyException(Kind.NOT_FOUND, "institution not found");
        }
        KmsOperation operation = operations.findById(command.operationId())
            .filter(found -> found.institutionId().equals(command.institutionId())
                && found.purpose() == command.purpose())
            .orElseThrow(() -> new SigningKeyException(Kind.NOT_FOUND, "operation not found"));
        if (!operation.inProgress()) {
            throw new SigningKeyException(Kind.STATE_CONFLICT,
                "operation is not in progress (" + operation.status() + ")", operation.id());
        }
        if (command.decision() == Decision.FAILED && operation.status() == KmsOperationStatus.RECORDED) {
            // §15.1 은 RECORDED 이후 재개만 말한다. T3 를 포기하는 전이는 두지 않는다 (계획 6 결정 9).
            throw new SigningKeyException(Kind.STATE_CONFLICT,
                "a RECORDED operation cannot be failed; adjudicate RECORDED to resume min_encryption_version",
                operation.id());
        }
        InstitutionSignKey master = keys.findById(operation.signKeyId())
            .orElseThrow(() -> new SigningKeyException(Kind.STATE_CONFLICT, "master disappeared", operation.id()));
        Instant now = clock.instant();
        String note = "[adjudicated " + command.decision() + " by " + command.requester() + " at " + now + "] "
            + command.evidence().strip();

        KmsOperation owned = own(operation, command.decision(), note, now);
        log.warn("KMS_OPERATION_ADJUDICATED operationId={} institutionId={} action={} status={} decision={} by={}",
            owned.id(), owned.institutionId(), owned.action(), owned.status(), command.decision(), command.requester());

        try {
            if (command.decision() == Decision.FAILED) {
                return fail(owned, master, now);
            }
            return switch (owned.action()) {
                case CREATE -> provisioning.resume(master, owned, command.requester());
                case ROTATE, COMPROMISE -> rotation.resume(master, owned, command.requester());
                case SUSPEND, RESUME, REVOKE -> throw new SigningKeyException(Kind.STATE_CONFLICT,
                    owned.action() + " operations have no external call to resume", owned.id());
            };
        } catch (RuntimeException exception) {
            // FAILED 판정(fail)이나 재개가 거부되면 운영자가 곧 다른 판정을 내릴 수 있게 소유권을 놓는다
            // (계획 6 최종 리뷰 Fix 2). 성공 경로는 T2/T3 가 놓는다.
            transactions.executeWithoutResult(status -> operations.releaseOwnership(owned.id(), identity.owner()));
            throw exception;
        }
    }

    /**
     * 소유권을 이 프로세스로 옮기고 근거를 남긴다. 다른 owner 의 lease 가 살아 있으면 거부한다 (§11.4).
     * {@code findById}(사전 확인)와 {@code acquireOwnership} 사이에 작업이 바뀔 수 있으므로(예: 다른 스레드의
     * T2 커밋으로 RECORDED 진입), 소유권을 잡은 뒤 이 트랜잭션 안에서 다시 읽어 그 fresh 값으로 진행 중 여부와
     * FAILED→RECORDED 금지 규칙을 재확인한다. 어긋나면 소유권 획득까지 롤백하고 그 자리에서 거부한다.
     */
    private KmsOperation own(KmsOperation operation, Decision decision, String note, Instant now) {
        return transactions.execute(status -> {
            if (!operations.acquireOwnership(operation.id(), identity.owner(), now, now.plus(KmsProcessIdentity.LEASE))) {
                throw new SigningKeyException(Kind.STATE_CONFLICT, "operation is still owned by "
                    + operation.owner() + " until its lease expires; retry later", operation.id());
            }
            KmsOperation fresh = operations.findById(operation.id())
                .orElseThrow(() -> new SigningKeyException(Kind.STATE_CONFLICT, "operation disappeared",
                    operation.id()));
            if (!fresh.inProgress()) {
                throw new SigningKeyException(Kind.STATE_CONFLICT,
                    "operation is not in progress (" + fresh.status() + ")", fresh.id());
            }
            if (decision == Decision.FAILED && fresh.status() == KmsOperationStatus.RECORDED) {
                // §15.1 은 RECORDED 이후 재개만 말한다. T3 를 포기하는 전이는 두지 않는다 (계획 6 결정 9).
                throw new SigningKeyException(Kind.STATE_CONFLICT,
                    "a RECORDED operation cannot be failed; adjudicate RECORDED to resume min_encryption_version",
                    fresh.id());
            }
            KmsOperation annotated = fresh.annotated(note, now);
            if (!operations.update(annotated)) {
                log.warn("kms operation {} was not updated: 0 rows", annotated.id());
            }
            return annotated;
        });
    }

    /** FAILED: 작업을 닫고, CREATE 의 PROVISIONING 마스터는 ERROR 로 (생성 API 재요청이 되살린다). 한 트랜잭션. */
    private KmsOperation fail(KmsOperation owned, InstitutionSignKey master, Instant now) {
        return transactions.execute(status -> {
            if (owned.action() == KmsAction.CREATE && master.status() == SignKeyStatus.PROVISIONING
                && !keys.update(master.markError("adjudicated FAILED (operation " + owned.id() + ")", now),
                    master.versionNo())) {
                throw new SigningKeyException(Kind.STATE_CONFLICT, "master changed concurrently", owned.id());
            }
            KmsOperation failed = owned.failed(owned.reason(), now);
            if (!operations.update(failed)) {
                log.warn("kms operation {} was not updated: 0 rows", failed.id());
            }
            if (!operations.releaseOwnership(failed.id(), identity.owner())) {
                log.warn("failed to release ownership for kms operation {}", failed.id());
            }
            return failed;
        });
    }
}
