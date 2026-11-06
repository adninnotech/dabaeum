package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.application.SigningKeyCommands.CreateKeys;
import com.adn.dabaeum.credential.application.SigningKeyException.Kind;
import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationConflictException;
import com.adn.dabaeum.credential.domain.KmsOperationRepository;
import com.adn.dabaeum.credential.domain.KmsOperationStatus;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyStatus;
import com.adn.dabaeum.credential.domain.TransitKeyAdminException;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import com.adn.dabaeum.credential.domain.TransitKeyInfo;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기관 키 생성 (설계서 §5.3, §5.4, §6.2 T1/T2, §11.4). 수료증 → Status List 순서로 마스터마다 T1 커밋 → 생성 호출
 * → 재조회 검증 → T2 커밋을 한 요청 안에서 끝낸다. 생성 호출은 같은 이름에 두 번 보내도 새 버전을 만들지 않으므로
 * 결과가 불확실하면 한 번 재조회해 확인한다. 회전에는 이 규칙을 쓰지 않는다 (§15.1).
 */
@Service
@Profile({"local", "dev"})
public class SigningKeyProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyProvisioningService.class);

    private final InstitutionRepository institutions;
    private final InstitutionSignKeyRepository keys;
    private final InstitutionSignKeyVersionRepository versions;
    private final KmsOperationRepository operations;
    private final TransitKeyAdminPort keyAdmin;
    private final CredentialPublicJwkFactory jwks;
    private final KmsProcessIdentity identity;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public SigningKeyProvisioningService(
        InstitutionRepository institutions,
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        KmsOperationRepository operations,
        ObjectProvider<TransitKeyAdminPort> keyAdmin,
        CredentialPublicJwkFactory jwks,
        KmsProcessIdentity identity,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this(institutions, keys, versions, operations, keyAdmin.getIfAvailable(), jwks, identity,
            transactionManager, clock);
    }

    SigningKeyProvisioningService(
        InstitutionRepository institutions,
        InstitutionSignKeyRepository keys,
        InstitutionSignKeyVersionRepository versions,
        KmsOperationRepository operations,
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
        this.keyAdmin = keyAdmin;
        this.jwks = jwks;
        this.identity = identity;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** 두 목적의 키를 만든다. 앞 목적이 실패하면 뒤 목적은 시작하지 않는다. */
    public List<KmsOperation> createKeys(CreateKeys command) {
        if (institutions.findById(command.institutionId()).isEmpty()) {
            throw new SigningKeyException(Kind.NOT_FOUND, "institution not found");
        }
        requireKeyAdmin();
        List<Target> targets = new ArrayList<>();
        for (SignKeyPurpose purpose : SignKeyPurpose.values()) {
            target(command.institutionId(), purpose).ifPresent(targets::add);
        }
        if (targets.isEmpty()) {
            throw new SigningKeyException(Kind.ALREADY_EXISTS, "signing keys already exist");
        }
        List<KmsOperation> results = new ArrayList<>();
        for (Target target : targets) {
            results.add(provision(target, command));
        }
        return results;
    }

    /** fresh: 이 요청이 INSERT 하는 새 마스터. retried: ERROR 에서 PROVISIONING 으로 되돌린 마스터(T1 에서 UPDATE). */
    private record Target(InstitutionSignKey master, boolean fresh, boolean retried) {
    }

    private Optional<Target> target(UUID institutionId, SignKeyPurpose purpose) {
        Instant now = clock.instant();
        Optional<InstitutionSignKey> current = keys.findCurrent(institutionId, purpose);
        if (current.isEmpty()) {
            List<InstitutionSignKey> retired = keys.findByInstitutionId(institutionId).stream()
                .filter(key -> key.purpose() == purpose).toList();
            retired.stream().filter(key -> key.status() != SignKeyStatus.COMPROMISED).findFirst()
                .ifPresent(key -> {
                    throw new SigningKeyException(Kind.STATE_CONFLICT, slug(purpose) + " key exists in a non-current "
                        + key.status() + " state; only COMPROMISED generations can be replaced");
                });
            // §6.5 세대 교체: 옛 세대가 전부 COMPROMISED 면 다음 세대를 만든다. 이름·kid 는 SignKeyNaming 이 세대까지 붙인다.
            int generation = retired.stream().mapToInt(InstitutionSignKey::generation).max().orElse(0) + 1;
            return Optional.of(new Target(InstitutionSignKey.provisioning(UUID.randomUUID(), institutionId, purpose,
                generation, now), true, false));
        }
        InstitutionSignKey master = current.get();
        return switch (master.status()) {
            case PROVISIONING -> Optional.of(new Target(master, false, false));
            // 계획 6 결정 11: 재요청이 곧 운영자 결정이다. 같은 이름에 다시 만들어도 버전은 늘지 않는다.
            case ERROR -> Optional.of(new Target(master.retryProvisioning(now), false, true));
            default -> Optional.empty();
        };
    }

    private KmsOperation provision(Target target, CreateKeys command) {
        InstitutionSignKey master = target.master();
        String idempotencyKey = command.idempotencyKey() + "/" + slug(master.purpose());
        KmsOperation operation = prepare(target, command, idempotencyKey);

        TransitKeyInfo info;
        try {
            if (!operations.reconfirmOwnership(operation.id(), identity.owner(), KmsOperationStatus.PREPARED,
                clock.instant(), clock.instant().plus(KmsProcessIdentity.LEASE))) {
                throw new SigningKeyException(Kind.STATE_CONFLICT, "operation ownership lost", operation.id());
            }
            keyAdmin.createKey(master.mount(), master.keyName());
        } catch (TransitKeyAdminException exception) {
            if (exception.reason() == TransitKeyAdminException.Reason.REJECTED) {
                fail(operation, master, "openbao rejected key creation");
                throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "openbao rejected key creation", operation.id());
            }
            operation = persist(operation.unconfirmed(clock.instant()));
        }
        Optional<TransitKeyInfo> read;
        try {
            read = keyAdmin.readKey(master.mount(), master.keyName());
        } catch (TransitKeyAdminException exception) {
            if (operation.status() == KmsOperationStatus.PREPARED) {
                persist(operation.unconfirmed(clock.instant()));
            }
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "key creation could not be confirmed", operation.id());
        }
        if (read.isEmpty()) {
            fail(operation, master, "key not created");
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "key was not created", operation.id());
        }
        info = read.get();
        if (!info.matchesDesignDefaults() || info.latestVersion() != 1) {
            fail(operation, master, "key config differs from design defaults");
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "key config differs from design defaults",
                operation.id());
        }
        return record(operation, master, info, command.requester());
    }

    /** T1: (새 마스터 INSERT) → 작업 PREPARED INSERT → 소유권 획득. 커밋. */
    private KmsOperation prepare(Target target, CreateKeys command, String idempotencyKey) {
        Instant now = clock.instant();
        KmsOperation operation = KmsOperation.prepared(UUID.randomUUID(), target.master(), KmsAction.CREATE, null,
            command.reason(), command.requester(), null, idempotencyKey, now);
        try {
            return transactions.execute(status -> {
                if (target.fresh()) {
                    keys.insert(target.master());
                } else if (target.retried()
                    && !keys.update(target.master(), target.master().versionNo() - 1)) {
                    throw new SigningKeyException(Kind.STATE_CONFLICT, "master changed concurrently");
                }
                operations.insert(operation);
                if (!operations.acquireOwnership(operation.id(), identity.owner(), now, now.plus(KmsProcessIdentity.LEASE))) {
                    throw new SigningKeyException(Kind.STATE_CONFLICT, "operation ownership unavailable",
                        operation.id());
                }
                return operation;
            });
        } catch (KmsOperationConflictException exception) {
            throw SigningKeyException.from(exception);
        } catch (DuplicateKeyException exception) {
            throw new SigningKeyException(Kind.STATE_CONFLICT, "signing key was created concurrently");
        }
    }

    /**
     * 운영자 판정 RECORDED (§15.1). 생성 호출이 착지했다고 운영자가 확인한 PREPARED·UNCONFIRMED 작업을 재조회로
     * 검증한 뒤 T2 로 끝낸다. createKey 를 다시 부르지 않는다. 키가 없으면 STATE_CONFLICT (FAILED 판정으로 안내),
     * 설계값이 어긋나면 생성 경로와 같이 FAILED·ERROR. 호출자가 이 프로세스로 소유권을 쥐고 있어야 한다.
     */
    public KmsOperation resume(InstitutionSignKey master, KmsOperation operation, UUID requester) {
        requireKeyAdmin();
        Optional<TransitKeyInfo> read;
        try {
            read = keyAdmin.readKey(master.mount(), master.keyName());
        } catch (TransitKeyAdminException exception) {
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "key could not be read; nothing changed",
                operation.id());
        }
        if (read.isEmpty()) {
            throw new SigningKeyException(Kind.STATE_CONFLICT,
                "key is not present in OpenBao; adjudicate FAILED instead", operation.id());
        }
        TransitKeyInfo info = read.get();
        if (!info.matchesDesignDefaults() || info.latestVersion() != 1) {
            fail(operation, master, "key config differs from design defaults");
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "key config differs from design defaults",
                operation.id());
        }
        return record(operation, master, info, requester);
    }

    private void requireKeyAdmin() {
        if (keyAdmin == null) {
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE, "key management is not configured");
        }
    }

    /** T2: 마스터 ACTIVE(v1) → 버전 1 행 → 작업 RECORDED·COMPLETED → 소유권 해제. 커밋. */
    private KmsOperation record(KmsOperation operation, InstitutionSignKey master, TransitKeyInfo info,
        UUID requester) {
        Instant now = clock.instant();
        TransitKeyInfo.Version v1 = info.version(1);
        try {
            return transactions.execute(status -> {
                InstitutionSignKey current = keys.findById(master.id()).orElseThrow(
                    () -> new SigningKeyException(Kind.STATE_CONFLICT, "master disappeared", operation.id()));
                InstitutionSignKey activated = current.activate(1, now);
                if (!keys.update(activated, current.versionNo())) {
                    throw new SigningKeyException(Kind.STATE_CONFLICT, "master changed concurrently", operation.id());
                }
                versions.insert(InstitutionSignKeyVersion.active(UUID.randomUUID(), activated.id(),
                    activated.institutionId(), activated.purpose(), activated.generation(), 1,
                    KmsValues.storedJwk(jwks, v1.publicKey()), v1.creationTime(), requester, now));
                KmsOperation completed = operation.recorded(1, KmsValues.master(activated), now).completed(now);
                updateOperation(completed);
                if (!operations.releaseOwnership(completed.id(), identity.owner())) {
                    log.warn("failed to release ownership for kms operation {}", completed.id());
                }
                return completed;
            });
        } catch (SigningKeyException exception) {
            // 회전(§15.1)과 같게 남긴다: OpenBao 에는 키가 있으나 DB 에 반영하지 못했으므로 관측 버전만
            // 남기고 운영자 판정(계획 6)에 맡긴다. PREPARED 로 두면 생성 사실을 DB 가 모르는 상태가 된다.
            unconfirm(operation, info, now);
            throw exception;
        } catch (RuntimeException exception) {
            // 예상치 못한 예외(예: current.activate 의 IllegalStateException)도 같은 자리에 남긴다.
            unconfirm(operation, info, now);
            throw new SigningKeyException(Kind.KMS_UNAVAILABLE,
                "key created in OpenBao but not recorded in the database", operation.id());
        }
    }

    /**
     * 작업을 UNCONFIRMED·관측 버전으로 남긴다. 생성 호출이 불확실했던 작업은 이미 UNCONFIRMED 로
     * 커밋되어 있으므로(§15.1) 그 상태에서 다시 전이시키지 않는다.
     */
    private void unconfirm(KmsOperation operation, TransitKeyInfo info, Instant now) {
        KmsOperation base = operation.status() == KmsOperationStatus.PREPARED
            ? operation.unconfirmed(now)
            : operation;
        persist(base.observed(info.latestVersion(), now));
    }

    private void fail(KmsOperation operation, InstitutionSignKey master, String reason) {
        Instant now = clock.instant();
        transactions.executeWithoutResult(status -> {
            keys.findById(master.id()).ifPresent(current -> {
                if (current.status() == SignKeyStatus.PROVISIONING
                    && !keys.update(current.markError(reason, now), current.versionNo())) {
                    log.warn("failed to mark sign key {} as ERROR: version changed concurrently", current.id());
                }
            });
            // 판정 재개(§15.1)에서 부른 경우 operation.reason() 에는 원래 요청 사유와 [adjudicated ...] 근거가
            // 이미 붙어 있다. failed() 는 reason 을 통째로 교체하므로, 먼저 annotate 해 근거를 합친 뒤 그 합친
            // 문자열을 실패 사유로 넣어야 근거가 사라지지 않는다 (계획 6 최종 리뷰 Fix 1).
            KmsOperation annotated = operation.annotated(reason, now);
            KmsOperation failed = annotated.failed(annotated.reason(), now);
            updateOperation(failed);
            if (!operations.releaseOwnership(failed.id(), identity.owner())) {
                log.warn("failed to release ownership for kms operation {}", failed.id());
            }
        });
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

    private static String slug(SignKeyPurpose purpose) {
        return purpose == SignKeyPurpose.CREDENTIAL ? "credential" : "status-list";
    }
}
