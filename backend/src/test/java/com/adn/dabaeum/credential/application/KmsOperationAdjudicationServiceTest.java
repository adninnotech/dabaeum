package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.application.SigningKeyCommands.Adjudicate;
import com.adn.dabaeum.credential.application.SigningKeyCommands.Adjudicate.Decision;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.FakeKeyAdmin;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryKeys;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryOperations;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryVersions;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.RollbackAwareTransactions;
import com.adn.dabaeum.credential.application.SigningKeyRotationServiceTest.InMemoryApprovals;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationRepository;
import com.adn.dabaeum.credential.domain.KmsOperationStatus;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyStatus;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class KmsOperationAdjudicationServiceTest {

    static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    static final UUID INSTITUTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID REQUESTER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID OPERATOR = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    static final String EVIDENCE = "audit 2026-09-13T23:59Z rotate by app token, latest_version 3";

    private final InMemoryKeys keys = new InMemoryKeys();
    private final InMemoryVersions versions = new InMemoryVersions();
    private final InMemoryOperations operations = new InMemoryOperations();
    private final InMemoryApprovals approvals = new InMemoryApprovals();
    private final FakeKeyAdmin keyAdmin = new FakeKeyAdmin();
    private final InstitutionRepository institutions = mock(InstitutionRepository.class);
    private InstitutionSignKey master;

    @BeforeEach
    void keyAtVersion2() {
        when(institutions.findById(INSTITUTION)).thenReturn(Optional.of(mock(Institution.class)));
        master = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1,
            NOW.minusSeconds(86400)).activate(1, NOW.minusSeconds(86400)).rotated(2, NOW.minusSeconds(43200));
        keys.insert(master);
        keyAdmin.createKey(master.mount(), master.keyName());
        keyAdmin.rotateKey(master.mount(), master.keyName());
        keyAdmin.calls.clear();
        for (int version = 1; version <= 2; version++) {
            InstitutionSignKeyVersion row = InstitutionSignKeyVersion.active(UUID.randomUUID(), master.id(),
                INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, version,
                KmsValues.storedJwk(SigningKeyProvisioningServiceTest.JWKS,
                    keyAdmin.publicKey(master.mount(), master.keyName(), version)),
                FakeKeyAdmin.CREATED_AT, REQUESTER, NOW.minusSeconds(86400));
            versions.insert(version == 1 ? row.retire(NOW.minusSeconds(43200)) : row);
        }
    }

    private KmsOperationAdjudicationService service(TransitKeyAdminPort port) {
        return service(port, operations);
    }

    /** operationsRepo 를 바꿔 끼울 수 있는 버전. 소유권 획득 경합을 흉내내는 래퍼를 넣을 때 쓴다. */
    private KmsOperationAdjudicationService service(TransitKeyAdminPort port, KmsOperationRepository operationsRepo) {
        PlatformTransactionManager transactions = new RollbackAwareTransactions(keys.rows, versions.rows,
            operations.rows, approvals.rows);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        KmsProcessIdentity identity = new KmsProcessIdentity("host-test");
        SigningKeyProvisioningService provisioning = new SigningKeyProvisioningService(institutions, keys, versions,
            operationsRepo, port, SigningKeyProvisioningServiceTest.JWKS, identity, transactions, clock);
        SigningKeyRotationService rotation = new SigningKeyRotationService(institutions, keys, versions,
            operationsRepo, approvals, new ApproverRoleCheck(mock(UserRoleRepository.class)), port,
            SigningKeyProvisioningServiceTest.JWKS, identity, transactions, clock);
        return new KmsOperationAdjudicationService(institutions, keys, operationsRepo, provisioning, rotation,
            identity, transactions, clock);
    }

    /** 다른 프로세스("dead-host")가 lease 를 넘겨 죽은 채 남긴 작업. */
    private KmsOperation orphaned(KmsOperation operation) {
        operations.insert(operation);
        assertThat(operations.acquireOwnership(operation.id(), "dead-host", NOW.minusSeconds(900),
            NOW.minusSeconds(600))).isTrue();
        return operations.findById(operation.id()).orElseThrow();
    }

    private Adjudicate command(UUID operationId, Decision decision) {
        return new Adjudicate(INSTITUTION, SignKeyPurpose.CREDENTIAL, operationId, OPERATOR, decision, EVIDENCE);
    }

    @Test
    void FAILED_판정은_작업을_FAILED_로_닫고_근거를_남기고_소유권을_놓는다() {
        KmsOperation stuck = orphaned(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null,
            "정기 교체", REQUESTER, UUID.randomUUID(), "ROTATE-1", NOW.minusSeconds(1000)).unconfirmed(NOW.minusSeconds(990)));

        KmsOperation result = service(keyAdmin).adjudicate(command(stuck.id(), Decision.FAILED));

        assertThat(result.status()).isEqualTo(KmsOperationStatus.FAILED);
        assertThat(result.reason()).startsWith("정기 교체\n[adjudicated FAILED by " + OPERATOR + " at " + NOW + "] ")
            .endsWith(EVIDENCE);
        assertThat(operations.rows.get(stuck.id()).owner()).isNull();
        assertThat(operations.findInProgressBySignKeyId(master.id())).isEmpty();
        assertThat(keyAdmin.calls).isEmpty();
        assertThat(keys.findById(master.id()).orElseThrow()).isEqualTo(master);
    }

    @Test
    void CREATE_작업의_FAILED_판정은_PROVISIONING_마스터를_ERROR_로_내린다() {
        InstitutionSignKey provisioning = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.STATUS_LIST, 1, NOW.minusSeconds(1000));
        keys.insert(provisioning);
        KmsOperation stuck = orphaned(KmsOperation.prepared(UUID.randomUUID(), provisioning, KmsAction.CREATE, null,
            "개설", REQUESTER, null, "CREATE-1/status-list", NOW.minusSeconds(1000)));

        KmsOperation result = service(keyAdmin).adjudicate(new Adjudicate(INSTITUTION, SignKeyPurpose.STATUS_LIST,
            stuck.id(), OPERATOR, Decision.FAILED, EVIDENCE));

        assertThat(result.status()).isEqualTo(KmsOperationStatus.FAILED);
        InstitutionSignKey error = keys.findById(provisioning.id()).orElseThrow();
        assertThat(error.status()).isEqualTo(SignKeyStatus.ERROR);
        assertThat(error.statusReason()).contains("adjudicated FAILED");
        assertThat(keyAdmin.calls).isEmpty();
    }

    @Test
    void FAILED_판정_자체가_마스터_UPDATE_충돌로_실패하면_소유권을_놓는다() {
        InstitutionSignKey provisioning = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.STATUS_LIST, 1, NOW.minusSeconds(1000));
        keys.insert(provisioning);
        KmsOperation stuck = orphaned(KmsOperation.prepared(UUID.randomUUID(), provisioning, KmsAction.CREATE, null,
            "개설", REQUESTER, null, "CREATE-9/status-list", NOW.minusSeconds(1000)));
        keys.updateConflict = true;

        assertThatThrownBy(() -> service(keyAdmin).adjudicate(new Adjudicate(INSTITUTION,
                SignKeyPurpose.STATUS_LIST, stuck.id(), OPERATOR, Decision.FAILED, EVIDENCE)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);

        KmsOperation stored = operations.findById(stuck.id()).orElseThrow();
        assertThat(stored.status()).isIn(KmsOperationStatus.PREPARED, KmsOperationStatus.UNCONFIRMED);
        assertThat(stored.owner()).isNull();
        assertThat(keys.findById(provisioning.id()).orElseThrow().status()).isEqualTo(SignKeyStatus.PROVISIONING);
    }

    @Test
    void RECORDED_판정은_회전_작업을_재개해_COMPLETED_로_끝낸다() {
        keyAdmin.rotateKey(master.mount(), master.keyName());   // 원요청의 rotate 는 착지했다
        keyAdmin.calls.clear();
        KmsOperation stuck = orphaned(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            REQUESTER, UUID.randomUUID(), "ROTATE-2", NOW.minusSeconds(1000)).unconfirmed(NOW.minusSeconds(990)));

        KmsOperation result = service(keyAdmin).adjudicate(command(stuck.id(), Decision.RECORDED));

        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(result.reason()).contains("[adjudicated RECORDED by " + OPERATOR);
        assertThat(keyAdmin.calls).containsExactly(
            "read education-sign/" + master.keyName(),
            "config education-sign/" + master.keyName() + " 3");
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(3);
        assertThat(operations.rows.get(stuck.id()).owner()).isNull();
    }

    @Test
    void RECORDED_판정은_생성_작업을_재개해_마스터를_ACTIVE_로_올린다() {
        InstitutionSignKey provisioning = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.STATUS_LIST, 1, NOW.minusSeconds(1000));
        keys.insert(provisioning);
        keyAdmin.createKey(provisioning.mount(), provisioning.keyName());
        keyAdmin.calls.clear();
        KmsOperation stuck = orphaned(KmsOperation.prepared(UUID.randomUUID(), provisioning, KmsAction.CREATE, null,
            "개설", REQUESTER, null, "CREATE-2/status-list", NOW.minusSeconds(1000)).unconfirmed(NOW.minusSeconds(990)));

        KmsOperation result = service(keyAdmin).adjudicate(new Adjudicate(INSTITUTION, SignKeyPurpose.STATUS_LIST,
            stuck.id(), OPERATOR, Decision.RECORDED, EVIDENCE));

        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(keyAdmin.calls).containsExactly("read education-status/" + provisioning.keyName());
        assertThat(keys.findById(provisioning.id()).orElseThrow().status()).isEqualTo(SignKeyStatus.ACTIVE);
    }

    @Test
    void 살아_있는_lease_를_다른_owner_가_쥐고_있으면_STATE_CONFLICT_로_거부하고_아무_것도_바꾸지_않는다() {
        KmsOperation live = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, "정기",
            REQUESTER, UUID.randomUUID(), "ROTATE-3", NOW.minusSeconds(30)).unconfirmed(NOW.minusSeconds(20));
        operations.insert(live);
        operations.acquireOwnership(live.id(), "other-host", NOW.minusSeconds(30), NOW.plusSeconds(270));

        assertThatThrownBy(() -> service(keyAdmin).adjudicate(command(live.id(), Decision.FAILED)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);

        KmsOperation stored = operations.findById(live.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
        assertThat(stored.reason()).isEqualTo("정기");
        assertThat(stored.owner()).isEqualTo("other-host");
        assertThat(keyAdmin.calls).isEmpty();
    }

    @Test
    void RECORDED_작업에_FAILED_판정은_STATE_CONFLICT_다() {
        InstitutionSignKey rotated = master.rotated(3, NOW);
        keys.update(rotated, master.versionNo());
        KmsOperation recorded = orphaned(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null,
            null, REQUESTER, UUID.randomUUID(), "ROTATE-4", NOW.minusSeconds(1000))
            .recorded(3, KmsValues.master(rotated), NOW.minusSeconds(900)));

        assertThatThrownBy(() -> service(keyAdmin).adjudicate(command(recorded.id(), Decision.FAILED)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        assertThat(operations.findById(recorded.id()).orElseThrow().status()).isEqualTo(KmsOperationStatus.RECORDED);
        assertThat(operations.rows.get(recorded.id()).owner()).isEqualTo("dead-host");
    }

    @Test
    void 진행_중이_아닌_작업은_STATE_CONFLICT_다른_기관이나_목적의_작업은_NOT_FOUND_다() {
        KmsOperation done = KmsOperation.completed(UUID.randomUUID(), master, KmsAction.SUSPEND, null, "감사",
            REQUESTER, null, "SUSPEND-1", "{}", "{}", NOW.minusSeconds(1000));
        operations.insert(done);
        KmsOperation stuck = orphaned(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            REQUESTER, UUID.randomUUID(), "ROTATE-5", NOW.minusSeconds(1000)));

        assertThatThrownBy(() -> service(keyAdmin).adjudicate(command(done.id(), Decision.FAILED)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        assertThatThrownBy(() -> service(keyAdmin).adjudicate(new Adjudicate(INSTITUTION,
                SignKeyPurpose.STATUS_LIST, stuck.id(), OPERATOR, Decision.FAILED, EVIDENCE)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.NOT_FOUND);
        assertThatThrownBy(() -> service(keyAdmin).adjudicate(command(UUID.randomUUID(), Decision.FAILED)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.NOT_FOUND);
        assertThat(operations.findById(stuck.id()).orElseThrow().status()).isEqualTo(KmsOperationStatus.PREPARED);
    }

    @Test
    void 재개가_거부되면_소유권을_놓아_다시_판정할_수_있다() {
        KmsOperation stuck = orphaned(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            REQUESTER, UUID.randomUUID(), "ROTATE-6", NOW.minusSeconds(1000)).unconfirmed(NOW.minusSeconds(990)));
        // rotate 는 착지하지 않았다 (latest 2) → RECORDED 판정은 틀렸다

        assertThatThrownBy(() -> service(keyAdmin).adjudicate(command(stuck.id(), Decision.RECORDED)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);

        KmsOperation stored = operations.findById(stuck.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
        assertThat(stored.observedVersionAfter()).isEqualTo(2);
        assertThat(stored.owner()).isNull();
        assertThat(stored.reason()).contains("[adjudicated RECORDED by");   // 시도 자체도 기록에 남는다

        KmsOperation failed = service(keyAdmin).adjudicate(command(stuck.id(), Decision.FAILED));
        assertThat(failed.status()).isEqualTo(KmsOperationStatus.FAILED);
    }

    @Test
    void 근거가_짧거나_없으면_명령_생성_단계에서_거부한다() {
        assertThatThrownBy(() -> new Adjudicate(INSTITUTION, SignKeyPurpose.CREDENTIAL, UUID.randomUUID(), OPERATOR,
            Decision.FAILED, "too short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Adjudicate(INSTITUTION, SignKeyPurpose.CREDENTIAL, UUID.randomUUID(), OPERATOR,
            Decision.FAILED, "x".repeat(401))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 소유권을_잡은_뒤_다시_읽어_그_사이_RECORDED_가_된_작업은_FAILED_로_닫지_않는다() {
        KmsOperation stuck = orphaned(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            REQUESTER, UUID.randomUUID(), "ROTATE-7", NOW.minusSeconds(1000)).unconfirmed(NOW.minusSeconds(990)));
        RecordedRaceOperations racing = new RecordedRaceOperations(operations, stuck.id());
        KmsOperationAdjudicationService service = service(keyAdmin, racing);

        assertThatThrownBy(() -> service.adjudicate(command(stuck.id(), Decision.FAILED)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);

        // acquireOwnership 과 재확인 사이의 경합을 흉내낸 시도는 own() 트랜잭션 전체(소유권 획득 포함)와 함께
        // 롤백된다 — 판정 이전(오손된 소유권을 쥔 "dead-host") 상태 그대로 남고 FAILED 로 닫히지 않는다.
        KmsOperation stored = operations.findById(stuck.id()).orElseThrow();
        assertThat(stored.status()).isNotEqualTo(KmsOperationStatus.FAILED);
        assertThat(stored.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
        assertThat(stored.reason()).isNull();
        assertThat(stored.owner()).isEqualTo("dead-host");
        assertThat(racing.raced).isTrue();
    }

    /**
     * 소유권 획득 시점에 그 사이 다른 프로세스가 T2 를 커밋해 RECORDED 로 만든 상황을 흉내낸다(§15.1 재확인 경합).
     * {@code acquireOwnership} 을 부르기 직전에 저장된 행을 {@code recorded(...)} 로 바꿔치기한 뒤 진짜 구현을
     * 그대로 호출한다 — 나머지 메서드는 위임한다.
     */
    private static final class RecordedRaceOperations implements KmsOperationRepository {
        private final InMemoryOperations delegate;
        private final UUID raceOperationId;
        boolean raced;

        RecordedRaceOperations(InMemoryOperations delegate, UUID raceOperationId) {
            this.delegate = delegate;
            this.raceOperationId = raceOperationId;
        }

        @Override public void insert(KmsOperation operation) { delegate.insert(operation); }
        @Override public Optional<KmsOperation> findById(UUID id) { return delegate.findById(id); }
        @Override public Optional<KmsOperation> findInProgressBySignKeyId(UUID signKeyId) {
            return delegate.findInProgressBySignKeyId(signKeyId);
        }
        @Override public List<KmsOperation> findByInstitutionId(UUID institutionId, int limit) {
            return delegate.findByInstitutionId(institutionId, limit);
        }
        @Override public List<KmsOperation> findInProgress(int limit) { return delegate.findInProgress(limit); }
        @Override public boolean acquireOwnership(UUID id, String owner, Instant now, Instant leaseUntil) {
            if (!raced && id.equals(raceOperationId)) {
                raced = true;
                KmsOperation row = delegate.rows.get(id);
                delegate.rows.put(id, row.recorded(3, "{}", now));
            }
            return delegate.acquireOwnership(id, owner, now, leaseUntil);
        }
        @Override public boolean reconfirmOwnership(UUID id, String owner, KmsOperationStatus expectedStatus,
            Instant now, Instant leaseUntil) {
            return delegate.reconfirmOwnership(id, owner, expectedStatus, now, leaseUntil);
        }
        @Override public boolean update(KmsOperation operation) { return delegate.update(operation); }
        @Override public boolean releaseOwnership(UUID id, String owner) { return delegate.releaseOwnership(id, owner); }
    }
}
