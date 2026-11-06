package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.FakeKeyAdmin;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryKeys;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryOperations;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.RollbackAwareTransactions;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationStatus;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class KmsRecoveryWorkerTest {

    static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    static final UUID INSTITUTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID REQUESTER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    /** 시험 중 시각을 앞으로 옮길 수 있는 시계. */
    static final class MutableClock extends Clock {
        Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final InMemoryKeys keys = new InMemoryKeys();
    private final InMemoryOperations operations = new InMemoryOperations();
    private final FakeKeyAdmin keyAdmin = new FakeKeyAdmin();
    private final MutableClock clock = new MutableClock();
    private InstitutionSignKey master;

    @BeforeEach
    void keyAtVersion2() {
        master = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1,
            NOW.minusSeconds(86400)).activate(1, NOW.minusSeconds(86400)).rotated(2, NOW.minusSeconds(43200));
        keys.insert(master);
        keyAdmin.createKey(master.mount(), master.keyName());
        keyAdmin.rotateKey(master.mount(), master.keyName());
        keyAdmin.calls.clear();
    }

    private KmsRecoveryWorker worker(TransitKeyAdminPort port) {
        return new KmsRecoveryWorker(operations, keys, port, new KmsProcessIdentity("host-test"),
            new RollbackAwareTransactions(keys.rows, operations.rows), clock);
    }

    private KmsOperation stuck(KmsOperationStatus status, String owner, Instant leaseUntil, String idempotencyKey) {
        KmsOperation operation = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            REQUESTER, UUID.randomUUID(), idempotencyKey, NOW.minusSeconds(1000));
        if (status == KmsOperationStatus.UNCONFIRMED) {
            operation = operation.unconfirmed(NOW.minusSeconds(990));
        } else if (status == KmsOperationStatus.RECORDED) {
            operation = operation.recorded(3, "{}", NOW.minusSeconds(980));
        }
        operations.insert(operation);
        if (owner != null) {
            assertThat(operations.acquireOwnership(operation.id(), owner, NOW.minusSeconds(1000), leaseUntil)).isTrue();
        }
        return operations.findById(operation.id()).orElseThrow();
    }

    private KmsOperation stored(KmsOperation operation) {
        return operations.findById(operation.id()).orElseThrow();
    }

    @Test
    void 다른_owner_의_lease_가_만료된_UNCONFIRMED_작업을_인계해_관측만_남기고_소유권을_놓는다() {
        keyAdmin.rotateKey(master.mount(), master.keyName());   // 죽은 프로세스의 rotate 는 착지했다 (latest 3)
        keyAdmin.calls.clear();
        KmsOperation orphan = stuck(KmsOperationStatus.UNCONFIRMED, "dead-host", NOW.minusSeconds(600), "R-1");

        int handled = worker(keyAdmin).runOnce(10);

        assertThat(handled).isEqualTo(1);
        assertThat(keyAdmin.calls).containsExactly("read education-sign/" + master.keyName());
        KmsOperation after = stored(orphan);
        assertThat(after.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);   // 자동 RECORDED 없음 (§15.1)
        assertThat(after.observedVersionAfter()).isEqualTo(3);
        assertThat(after.owner()).isNull();
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
    }

    @Test
    void lease_가_살아_있는_작업은_남의_것이든_내_것이든_건드리지_않는다() {
        KmsOperation other = stuck(KmsOperationStatus.UNCONFIRMED, "other-host", NOW.plusSeconds(200), "R-2");

        assertThat(worker(keyAdmin).runOnce(10)).isZero();
        assertThat(keyAdmin.calls).isEmpty();
        assertThat(stored(other).owner()).isEqualTo("other-host");
        assertThat(stored(other).observedVersionAfter()).isNull();

        operations.rows.clear();
        KmsOperation mine = stuck(KmsOperationStatus.PREPARED, "host-test", NOW.plusSeconds(200), "R-3");
        assertThat(worker(keyAdmin).runOnce(10)).isZero();
        assertThat(stored(mine).owner()).isEqualTo("host-test");
    }

    @Test
    void PREPARED_작업은_상태를_바꾸지_않고_관측만_한다() {
        KmsOperation orphan = stuck(KmsOperationStatus.PREPARED, "dead-host", NOW.minusSeconds(600), "R-4");

        worker(keyAdmin).runOnce(10);

        assertThat(stored(orphan).status()).isEqualTo(KmsOperationStatus.PREPARED);
        assertThat(stored(orphan).observedVersionAfter()).isEqualTo(2);
        assertThat(stored(orphan).owner()).isNull();
    }

    @Test
    void RECORDED_작업과_owner_없는_작업도_처리하되_RECORDED_는_읽지_않는다() {
        KmsOperation recorded = stuck(KmsOperationStatus.RECORDED, "dead-host", NOW.minusSeconds(600), "R-5");
        InstitutionSignKey statusList = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.STATUS_LIST, 1, NOW.minusSeconds(86400)).activate(1, NOW.minusSeconds(86400));
        keys.insert(statusList);
        keyAdmin.createKey(statusList.mount(), statusList.keyName());
        keyAdmin.calls.clear();
        KmsOperation ownerless = KmsOperation.prepared(UUID.randomUUID(), statusList, KmsAction.ROTATE, null, null,
            REQUESTER, UUID.randomUUID(), "R-6", NOW.minusSeconds(1000)).unconfirmed(NOW.minusSeconds(990));
        operations.insert(ownerless);

        assertThat(worker(keyAdmin).runOnce(10)).isEqualTo(2);

        // 두 작업(RECORDED 하나, UNCONFIRMED 하나)을 처리한 뒤에도 호출은 read 하나뿐이다 — 이 containsExactly
        // 자체가 rotate·config·create 가 전혀 불리지 않았음을 이미 증명하므로 별도 noneMatch 는 두지 않는다.
        assertThat(keyAdmin.calls).containsExactly("read education-status/" + statusList.keyName());
        assertThat(stored(recorded).status()).isEqualTo(KmsOperationStatus.RECORDED);
        assertThat(stored(recorded).owner()).isNull();
        assertThat(stored(ownerless).observedVersionAfter()).isEqualTo(1);
    }

    @Test
    void 키_관리자_포트가_없거나_읽기가_실패해도_경고하고_다음_작업으로_넘어간다() {
        KmsOperation first = stuck(KmsOperationStatus.UNCONFIRMED, "dead-host", NOW.minusSeconds(600), "R-7");

        assertThat(worker(null).runOnce(10)).isEqualTo(1);
        assertThat(stored(first).observedVersionAfter()).isNull();
        assertThat(stored(first).owner()).isNull();

        keyAdmin.readFailure = true;
        assertThat(worker(keyAdmin).runOnce(10)).isEqualTo(1);
        assertThat(stored(first).observedVersionAfter()).isNull();
        assertThat(stored(first).status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
    }

    @Test
    void 같은_작업은_30분_안에_다시_경고하지_않고_배치_크기를_지킨다() {
        KmsOperation orphan = stuck(KmsOperationStatus.UNCONFIRMED, "dead-host", NOW.minusSeconds(600), "R-8");
        KmsRecoveryWorker worker = worker(keyAdmin);

        worker.runOnce(10);
        Instant firstAlert = worker.lastAlertAt(orphan.id()).orElseThrow();
        clock.now = NOW.plus(Duration.ofMinutes(10));
        worker.runOnce(10);
        assertThat(worker.lastAlertAt(orphan.id())).contains(firstAlert);
        clock.now = NOW.plus(Duration.ofMinutes(31));
        worker.runOnce(10);
        assertThat(worker.lastAlertAt(orphan.id())).contains(NOW.plus(Duration.ofMinutes(31)));

        assertThat(worker.runOnce(0)).isZero();
    }

    @Test
    void 소유권을_잡은_뒤_다시_읽어_그_사이_RECORDED_가_된_작업은_관측으로_덮어쓰지_않는다() {
        KmsOperation orphan = stuck(KmsOperationStatus.UNCONFIRMED, "dead-host", NOW.minusSeconds(600), "R-9");
        InMemoryOperations spyOperations = Mockito.spy(operations);
        // acquireOwnership 이 실제로 소유권을 옮기기 직전, 운영자 판정이 그 사이 끼어들어 RECORDED 로 만들고
        // 근거를 남겼다고 흉내낸다. 워커는 acquireOwnership 뒤 다시 읽은 값으로 이어가야 이 판정을 잃지 않는다.
        Mockito.doAnswer(invocation -> {
            KmsOperation recordedByOperator = orphan.recorded(3, "{}", NOW)
                .annotated("[adjudicated RECORDED by x at t] evidence", NOW);
            operations.rows.put(recordedByOperator.id(), recordedByOperator);
            return invocation.callRealMethod();
        }).when(spyOperations).acquireOwnership(eq(orphan.id()), any(), any(), any());

        KmsRecoveryWorker worker = new KmsRecoveryWorker(spyOperations, keys, keyAdmin,
            new KmsProcessIdentity("host-test"), new RollbackAwareTransactions(keys.rows, operations.rows), clock);

        int handled = worker.runOnce(10);

        assertThat(handled).isEqualTo(1);
        assertThat(keyAdmin.calls).isEmpty();   // RECORDED 는 재조회로 읽지 않는다
        KmsOperation after = stored(orphan);
        assertThat(after.status()).isEqualTo(KmsOperationStatus.RECORDED);
        assertThat(after.reason()).contains("[adjudicated RECORDED by x at t] evidence");
        assertThat(after.owner()).isNull();
    }

    @Test
    void 자기_프로세스가_소유했던_lease_가_만료된_작업도_다시_집어_관측한다() {
        KmsOperation mine = stuck(KmsOperationStatus.UNCONFIRMED, "host-test", NOW.minusSeconds(600), "R-10");

        assertThat(worker(keyAdmin).runOnce(10)).isEqualTo(1);

        KmsOperation after = stored(mine);
        assertThat(after.observedVersionAfter()).isEqualTo(2);
        assertThat(after.owner()).isNull();
    }

    @Test
    void 경고_시각_맵은_상한을_넘기지_않는다() {
        KmsRecoveryWorker worker = worker(keyAdmin);
        for (int i = 0; i < KmsRecoveryWorker.MAX_TRACKED_ALERTS + 5; i++) {
            InstitutionSignKey extra = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
                SignKeyPurpose.CREDENTIAL, i + 2, NOW).activate(1, NOW);
            keys.insert(extra);
            operations.insert(KmsOperation.prepared(UUID.randomUUID(), extra, KmsAction.ROTATE, null, null,
                REQUESTER, UUID.randomUUID(), "CAP-" + i, NOW.minusSeconds(1000)).unconfirmed(NOW.minusSeconds(990)));
        }
        worker.runOnce(KmsRecoveryWorker.MAX_TRACKED_ALERTS + 5);
        assertThat(worker.trackedAlerts()).isLessThanOrEqualTo(KmsRecoveryWorker.MAX_TRACKED_ALERTS);
    }
}
