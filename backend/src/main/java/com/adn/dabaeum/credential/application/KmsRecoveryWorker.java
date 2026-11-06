package com.adn.dabaeum.credential.application;

import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationRepository;
import com.adn.dabaeum.credential.domain.KmsOperationStatus;
import com.adn.dabaeum.credential.domain.TransitKeyAdminException;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import com.adn.dabaeum.credential.domain.TransitKeyInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 복구 워커 (설계서 §15.1, §16). 진행 중 작업을 인계 조건 안에서 집어 관측·경고만 한다. rotate·config·create 를
 * 부르지 않고, 시간 경과나 버전 증가로 RECORDED·FAILED 를 확정하지 않는다 — 그것은 운영자 판정이다.
 * 첫 사이클(기동 10초 뒤)이 "기동 시 인계" 를 대신한다. 인계 조건은 owner ≠ 자기 식별자(단일 JVM 전제, §21)이며
 * acquireOwnership 이 lease 만료도 요구하므로 인계는 lease(5분) 뒤에 일어난다. 관측 뒤 소유권을 놓아 운영자
 * 판정이 즉시 집을 수 있게 한다.
 */
public final class KmsRecoveryWorker {

    private static final Logger log = LoggerFactory.getLogger(KmsRecoveryWorker.class);

    static final Duration REALERT = Duration.ofMinutes(30);
    static final int MAX_TRACKED_ALERTS = 1000;

    private final KmsOperationRepository operations;
    private final InstitutionSignKeyRepository keys;
    private final TransitKeyAdminPort keyAdmin;
    private final KmsProcessIdentity identity;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Map<UUID, Instant> alertedAt = new ConcurrentHashMap<>();

    /** keyAdmin 이 null 이면(provider=local) 관측 없이 경고만 한다. */
    public KmsRecoveryWorker(
        KmsOperationRepository operations,
        InstitutionSignKeyRepository keys,
        TransitKeyAdminPort keyAdmin,
        KmsProcessIdentity identity,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.keyAdmin = keyAdmin;
        this.identity = Objects.requireNonNull(identity, "identity");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 한 사이클. 관측·경고한 작업 수를 돌려준다. 한 작업의 예외가 다른 작업을 막지 않는다. */
    public int runOnce(int batchSize) {
        if (batchSize <= 0) {
            return 0;
        }
        Instant now = clock.instant();
        List<KmsOperation> stuck = operations.findInProgress(batchSize);
        if (stuck.size() < batchSize) {
            // 전체를 다 봤을 때만 끝난 작업의 경고 시각을 정리한다 (부분 배치에서 지우면 다음 사이클에 다시 경고한다).
            Set<UUID> ids = stuck.stream().map(KmsOperation::id).collect(Collectors.toSet());
            alertedAt.keySet().retainAll(ids);
        }
        int handled = 0;
        for (KmsOperation operation : stuck) {
            try {
                if (handle(operation, now)) {
                    handled++;
                }
            } catch (RuntimeException exception) {
                log.warn("KMS_RECOVERY_ITEM_FAILED operationId={} type={}", operation.id(),
                    exception.getClass().getName(), exception);
            }
        }
        return handled;
    }

    /** 시험용. 마지막 경고 시각. */
    Optional<Instant> lastAlertAt(UUID operationId) {
        return Optional.ofNullable(alertedAt.get(operationId));
    }

    /** 시험용. 추적 중인 경고 시각 수. */
    int trackedAlerts() {
        return alertedAt.size();
    }

    private boolean handle(KmsOperation operation, Instant now) {
        boolean leaseAlive = operation.leaseUntil() != null && !operation.leaseUntil().isBefore(now);
        if (leaseAlive) {
            // 같은 프로세스의 스레드가 진행 중이거나, 다른 owner 의 lease 가 아직 남아 있다. §15.1: lease 만료 전 인계 없음.
            return false;
        }
        boolean takeover = operation.owner() != null && !identity.owner().equals(operation.owner());
        UUID operationId = operation.id();
        boolean acquired = Boolean.TRUE.equals(transactions.execute(status ->
            operations.acquireOwnership(operationId, identity.owner(), now, now.plus(KmsProcessIdentity.LEASE))));
        if (!acquired) {
            return false;
        }
        // acquireOwnership 과 여기 사이에 운영자 판정이 상태를 바꿀 수 있다(예: RECORDED 로 진입하며 근거를
        // 남김). 그 사이의 값을 다시 읽어야 관측이 판정을 덮어쓰지 않는다 (KmsOperationAdjudicationService.own 참고).
        Optional<KmsOperation> fresh = operations.findById(operationId).filter(KmsOperation::inProgress);
        if (fresh.isEmpty()) {
            transactions.executeWithoutResult(status -> operations.releaseOwnership(operationId, identity.owner()));
            return false;
        }
        try {
            KmsOperation current = fresh.get();
            if (keyAdmin != null && current.status() != KmsOperationStatus.RECORDED) {
                current = observe(current, now);
            }
            alert(current, takeover, now);
        } finally {
            transactions.executeWithoutResult(status -> operations.releaseOwnership(operationId, identity.owner()));
        }
        return true;
    }

    /** §15.1 2단계: latest_version 을 읽어 observed_version_after 에 기록만 한다. 상태는 바꾸지 않는다. */
    private KmsOperation observe(KmsOperation operation, Instant now) {
        Optional<InstitutionSignKey> master = keys.findById(operation.signKeyId());
        if (master.isEmpty()) {
            return operation;
        }
        try {
            Optional<TransitKeyInfo> info = keyAdmin.readKey(master.get().mount(), master.get().keyName());
            if (info.isEmpty()) {
                return operation;   // 키가 없다는 사실도 운영자가 판정한다. 여기서 FAILED 로 확정하지 않는다.
            }
            KmsOperation observed = operation.observed(info.get().latestVersion(), now);
            transactions.executeWithoutResult(status -> {
                if (!operations.update(observed)) {
                    log.warn("kms operation {} was not updated: 0 rows", observed.id());
                }
            });
            return observed;
        } catch (TransitKeyAdminException exception) {
            log.warn("KMS_RECOVERY_READ_FAILED operationId={} reason={}", operation.id(), exception.reason());
            return operation;
        }
    }

    /** §16: 발생 즉시, 같은 작업은 30분마다. 연계키만 싣고 비밀값은 없다. */
    private void alert(KmsOperation operation, boolean takeover, Instant now) {
        Instant last = alertedAt.get(operation.id());
        if (last != null && last.plus(REALERT).isAfter(now)) {
            return;
        }
        if (alertedAt.size() >= MAX_TRACKED_ALERTS && !alertedAt.containsKey(operation.id())) {
            // 배치가 매 사이클 꽉 차면 retainAll 이 돌지 않는다. 가장 오래된 경고 시각을 버려 상한을 지킨다.
            alertedAt.entrySet().stream().min(Map.Entry.comparingByValue())
                .ifPresent(oldest -> alertedAt.remove(oldest.getKey()));
        }
        alertedAt.put(operation.id(), now);
        Optional<InstitutionSignKey> master = keys.findById(operation.signKeyId());
        log.warn("KMS_OPERATION_STUCK operationId={} institutionId={} mount={} keyName={} action={} status={} "
                + "expectedVersionBefore={} observedVersionAfter={} takeover={} previousOwner={} preparedAt={} "
                + "lastCallAt={} — adjudicate via POST .../signing-keys/{purpose}/adjudicate",
            operation.id(), operation.institutionId(), master.map(InstitutionSignKey::mount).orElse("?"),
            master.map(InstitutionSignKey::keyName).orElse("?"), operation.action(), operation.status(),
            operation.expectedVersionBefore(), operation.observedVersionAfter(), takeover, operation.owner(),
            operation.preparedAt(), operation.lastCallAt());
    }
}
