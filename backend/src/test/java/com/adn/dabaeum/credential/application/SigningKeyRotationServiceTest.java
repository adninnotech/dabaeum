package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.application.SigningKeyCommands.Rotate;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.FakeKeyAdmin;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryKeys;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryOperations;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryVersions;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.RollbackAwareTransactions;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsApproval;
import com.adn.dabaeum.credential.domain.KmsApprovalConsumption;
import com.adn.dabaeum.credential.domain.KmsApprovalParams;
import com.adn.dabaeum.credential.domain.KmsApprovalRepository;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationStatus;
import com.adn.dabaeum.credential.domain.SignKeyNaming;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.credential.domain.TransitKeyAdminException;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SigningKeyRotationServiceTest {

    static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    static final UUID INSTITUTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID REQUESTER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID APPROVER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    static final String KEY = "ROTATE-2027";

    private final InMemoryKeys keys = new InMemoryKeys();
    private final InMemoryVersions versions = new InMemoryVersions();
    private final InMemoryOperations operations = new InMemoryOperations();
    private final InMemoryApprovals approvals = new InMemoryApprovals();
    private final FakeKeyAdmin keyAdmin = new FakeKeyAdmin();
    private final InstitutionRepository institutions = mock(InstitutionRepository.class);
    private final UserRoleRepository userRoles = mock(UserRoleRepository.class);
    private InstitutionSignKey master;
    private KmsApproval approval;

    @BeforeEach
    void provisionedKeyAtVersion2() {
        when(institutions.findById(INSTITUTION)).thenReturn(Optional.of(mock(Institution.class)));
        when(userRoles.findByUserId(APPROVER)).thenReturn(List.of(new UserRoleAssignment(UUID.randomUUID(),
            APPROVER, null, UserRole.PLATFORM_ADMIN, NOW)));
        master = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, NOW)
            .activate(1, NOW).rotated(2, NOW);
        keys.insert(master);
        keyAdmin.createKey(master.mount(), master.keyName());
        keyAdmin.rotateKey(master.mount(), master.keyName());
        keyAdmin.calls.clear();
        for (int version = 1; version <= 2; version++) {
            InstitutionSignKeyVersion row = InstitutionSignKeyVersion.active(UUID.randomUUID(), master.id(),
                INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, version,
                KmsValues.storedJwk(SigningKeyProvisioningServiceTest.JWKS,
                    keyAdmin.publicKey(master.mount(), master.keyName(), version)),
                FakeKeyAdmin.CREATED_AT, REQUESTER, NOW);
            versions.insert(version == 1 ? row.retire(NOW) : row);
        }
        approval = KmsApproval.create(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            KmsApprovalParams.hash(KmsAction.ROTATE, SignKeyPurpose.CREDENTIAL, KEY), APPROVER, NOW);
        approvals.insert(approval);
    }

    private SigningKeyRotationService service(TransitKeyAdminPort port) {
        return new SigningKeyRotationService(institutions, keys, versions, operations, approvals,
            new ApproverRoleCheck(userRoles), port,
            SigningKeyProvisioningServiceTest.JWKS, new KmsProcessIdentity("host-test"),
            new RollbackAwareTransactions(keys.rows, versions.rows, operations.rows, approvals.rows),
            Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));
    }

    private Rotate command(UUID approvalId, String idempotencyKey) {
        return new Rotate(INSTITUTION, SignKeyPurpose.CREDENTIAL, REQUESTER, approvalId, idempotencyKey);
    }

    /** 작업 행을 직접 넣고 이 프로세스("host-test")가 소유권을 쥔 상태를 만든다 — T1 을 지난 뒤의 모습. */
    private KmsOperation owned(KmsOperation operation) {
        operations.insert(operation);
        assertThat(operations.acquireOwnership(operation.id(), "host-test", NOW, NOW.plusSeconds(300))).isTrue();
        return operations.findById(operation.id()).orElseThrow();
    }

    @Test
    void 후속_회전은_COMPROMISE_작업을_그대로_이어가고_COMPROMISED_버전을_건드리지_않는다() {
        InstitutionSignKeyVersion v2 = versions.findBySignKeyIdAndVersion(master.id(), 2).orElseThrow();
        versions.compromise(v2.id(), NOW.minusSeconds(3600));
        KmsOperation prepared = owned(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.COMPROMISE, 2,
            "유출", REQUESTER, approval.id(), "COMPROMISE-1", NOW));

        KmsOperation result = service(keyAdmin).continueRotation(master, prepared, REQUESTER);

        assertThat(keyAdmin.calls).containsExactly(
            "rotate education-sign/" + master.keyName(),
            "read education-sign/" + master.keyName(),
            "config education-sign/" + master.keyName() + " 3");
        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(result.action()).isEqualTo(KmsAction.COMPROMISE);
        assertThat(result.targetKeyVersion()).isEqualTo(2);
        assertThat(result.observedVersionAfter()).isEqualTo(3);
        assertThat(versions.findBySignKeyIdAndVersion(master.id(), 2).orElseThrow().status())
            .isEqualTo(SignKeyVersionStatus.COMPROMISED);
        assertThat(versions.findBySignKeyIdAndVersion(master.id(), 3).orElseThrow().status())
            .isEqualTo(SignKeyVersionStatus.ACTIVE);
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(3);
        assertThat(operations.rows.get(result.id()).owner()).isNull();
    }

    @Test
    void 판정_RECORDED_재개는_UNCONFIRMED_작업을_재조회로_확인해_rotate_없이_T2_이후를_이어간다() {
        keyAdmin.rotateKey(master.mount(), master.keyName());   // 원요청의 rotate 는 착지했고 응답만 유실된 상황
        keyAdmin.calls.clear();
        KmsOperation unconfirmed = owned(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null,
            null, REQUESTER, approval.id(), KEY, NOW).unconfirmed(NOW));

        KmsOperation result = service(keyAdmin).resume(master, unconfirmed, REQUESTER);

        assertThat(keyAdmin.calls).containsExactly(
            "read education-sign/" + master.keyName(),
            "config education-sign/" + master.keyName() + " 3");
        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(result.observedVersionAfter()).isEqualTo(3);
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(3);
        assertThat(versions.findBySignKeyIdAndVersion(master.id(), 2).orElseThrow().status())
            .isEqualTo(SignKeyVersionStatus.RETIRED);
        assertThat(operations.rows.get(result.id()).owner()).isNull();
    }

    @Test
    void 재개_시_관측_버전이_기대와_다르면_STATE_CONFLICT_이고_상태는_그대로_관측만_남긴다() {
        KmsOperation unconfirmed = owned(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null,
            null, REQUESTER, approval.id(), KEY, NOW).unconfirmed(NOW));   // rotate 는 착지하지 않았다 (latest 2)

        assertThatThrownBy(() -> service(keyAdmin).resume(master, unconfirmed, REQUESTER))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
                assertThat(failure.operationId()).isEqualTo(unconfirmed.id());
            });

        KmsOperation stored = operations.findById(unconfirmed.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
        assertThat(stored.observedVersionAfter()).isEqualTo(2);
        assertThat(keyAdmin.calls).containsExactly("read education-sign/" + master.keyName());
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
    }

    @Test
    void RECORDED_작업의_재개는_config_와_T3_만_한다() {
        keyAdmin.rotateKey(master.mount(), master.keyName());
        keyAdmin.calls.clear();
        InstitutionSignKey rotated = master.rotated(3, NOW);
        keys.update(rotated, master.versionNo());
        KmsOperation recorded = owned(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null,
            null, REQUESTER, approval.id(), KEY, NOW).recorded(3, KmsValues.master(rotated), NOW));

        KmsOperation result = service(keyAdmin).resume(rotated, recorded, REQUESTER);

        assertThat(keyAdmin.calls).containsExactly(
            "read education-sign/" + master.keyName(),
            "config education-sign/" + master.keyName() + " 3");
        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(keyAdmin.minEncryption.get(master.mount() + "/" + master.keyName())).isEqualTo(3);
        assertThat(operations.rows.get(result.id()).owner()).isNull();
    }

    @Test
    void 재개_재조회가_실패하면_KMS_UNAVAILABLE_이고_아무_것도_바꾸지_않는다() {
        keyAdmin.readFailure = true;
        KmsOperation unconfirmed = owned(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null,
            null, REQUESTER, approval.id(), KEY, NOW).unconfirmed(NOW));

        assertThatThrownBy(() -> service(keyAdmin).resume(master, unconfirmed, REQUESTER))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
        KmsOperation stored = operations.findById(unconfirmed.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
        assertThat(stored.observedVersionAfter()).isNull();
        assertThat(stored.owner()).isEqualTo("host-test");
    }

    @Test
    void 승인을_소비하고_회전_재조회_T2_min_encryption_version_T3_순서로_끝낸다() {
        KmsOperation result = service(keyAdmin).rotate(command(approval.id(), KEY));

        assertThat(keyAdmin.calls).containsExactly(
            "rotate education-sign/" + master.keyName(),
            "read education-sign/" + master.keyName(),
            "config education-sign/" + master.keyName() + " 3");
        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(result.expectedVersionBefore()).isEqualTo(2);
        assertThat(result.observedVersionAfter()).isEqualTo(3);
        assertThat(result.approvalId()).isEqualTo(approval.id());
        assertThat(operations.rows.get(result.id()).owner()).isNull();
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedByOperationId()).isEqualTo(result.id());

        InstitutionSignKey rotated = keys.findById(master.id()).orElseThrow();
        assertThat(rotated.currentVersion()).isEqualTo(3);
        assertThat(rotated.rotatedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(versions.findBySignKeyIdAndVersion(master.id(), 2).orElseThrow().status())
            .isEqualTo(SignKeyVersionStatus.RETIRED);
        InstitutionSignKeyVersion v3 = versions.findBySignKeyIdAndVersion(master.id(), 3).orElseThrow();
        assertThat(v3.status()).isEqualTo(SignKeyVersionStatus.ACTIVE);
        assertThat(v3.kidFragment()).isEqualTo(SignKeyNaming.kidFragment(SignKeyPurpose.CREDENTIAL, 1, 3));
        assertThat(SigningKeyProvisioningServiceTest.JWKS.publicKey(v3.publicKeyJwk()).getEncoded())
            .isEqualTo(keyAdmin.publicKey(master.mount(), master.keyName(), 3).getEncoded());
        assertThat(keyAdmin.minEncryption.get(master.mount() + "/" + master.keyName())).isEqualTo(3);
    }

    @Test
    void 승인이_맞지_않으면_OpenBao_를_부르지_않고_거부한다() {
        Rotate selfApproved = new Rotate(INSTITUTION, SignKeyPurpose.CREDENTIAL, APPROVER, approval.id(), KEY);
        assertThatThrownBy(() -> service(keyAdmin).rotate(selfApproved))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.APPROVAL_REJECTED);
        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), "OTHER-KEY")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.APPROVAL_REJECTED);
        assertThatThrownBy(() -> service(keyAdmin).rotate(command(UUID.randomUUID(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.APPROVAL_REJECTED);

        assertThat(keyAdmin.calls).isEmpty();
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedAt()).isNull();
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
    }

    @Test
    void 승인자가_PLATFORM_ADMIN_을_잃었으면_T1_에서_거부하고_승인도_작업도_남기지_않는다() {
        when(userRoles.findByUserId(APPROVER)).thenReturn(List.of(new UserRoleAssignment(UUID.randomUUID(),
            APPROVER, INSTITUTION, UserRole.INSTITUTION_ADMIN, NOW)));

        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.APPROVAL_REJECTED);

        assertThat(keyAdmin.calls).isEmpty();
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedAt()).isNull();
        assertThat(operations.rows).isEmpty();
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
    }

    @Test
    void 회전_호출이_불확실하면_UNCONFIRMED_로_남기고_재호출하지_않는다() {
        keyAdmin.rotateFailure = TransitKeyAdminException.Reason.UNAVAILABLE;
        keyAdmin.rotateDespiteFailure = true;

        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
                KmsOperation operation = operations.findById(failure.operationId()).orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
                assertThat(operation.observedVersionAfter()).isNull();
            });
        assertThat(keyAdmin.calls).containsExactly("rotate education-sign/" + master.keyName());
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
        assertThat(versions.findBySignKeyIdAndVersion(master.id(), 3)).isEmpty();
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedAt()).isNotNull();
        assertThat(operations.findInProgressBySignKeyId(master.id())).isPresent();
    }

    @Test
    void 재조회_버전이_n_plus_1_이_아니면_관측만_기록하고_자동_판정하지_않는다() {
        keyAdmin.extraVersionsOnRotate = 1;

        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                KmsOperation operation = operations.findById(((SigningKeyException) exception).operationId())
                    .orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
                assertThat(operation.observedVersionAfter()).isEqualTo(4);
            });
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
        assertThat(versions.findBySignKeyIdAndVersion(master.id(), 2).orElseThrow().status())
            .isEqualTo(SignKeyVersionStatus.ACTIVE);
        assertThat(keyAdmin.calls).noneMatch(call -> call.startsWith("config"));
    }

    @Test
    void min_encryption_version_은_max_로_쓰고_실패하면_한_번만_더_시도한_뒤_RECORDED_로_남긴다() {
        keyAdmin.minEncryption.put(master.mount() + "/" + master.keyName(), 5);
        keyAdmin.configFailuresRemaining = 1;
        KmsOperation result = service(keyAdmin).rotate(command(approval.id(), KEY));
        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(keyAdmin.calls.stream().filter(call -> call.startsWith("config")).toList())
            .containsExactly("config education-sign/" + master.keyName() + " 5",
                "config education-sign/" + master.keyName() + " 5");

        KmsApproval second = KmsApproval.create(UUID.randomUUID(), keys.findById(master.id()).orElseThrow(),
            KmsAction.ROTATE, null, null, KmsApprovalParams.hash(KmsAction.ROTATE, SignKeyPurpose.CREDENTIAL, "R2"),
            APPROVER, NOW);
        approvals.insert(second);
        keyAdmin.configFailuresRemaining = 2;
        assertThatThrownBy(() -> service(keyAdmin).rotate(command(second.id(), "R2")))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
                assertThat(operations.findById(failure.operationId()).orElseThrow().status())
                    .isEqualTo(KmsOperationStatus.RECORDED);
            });
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(4);
        assertThat(versions.findBySignKeyIdAndVersion(master.id(), 4).orElseThrow().status())
            .isEqualTo(SignKeyVersionStatus.ACTIVE);
    }

    @Test
    void COMPROMISED_버전은_RETIRED_로_바뀌지_않고_회전은_진행된다() {
        InstitutionSignKeyVersion v2 = versions.findBySignKeyIdAndVersion(master.id(), 2).orElseThrow();
        versions.compromise(v2.id(), NOW.minusSeconds(60));

        service(keyAdmin).rotate(command(approval.id(), KEY));

        assertThat(versions.findBySignKeyIdAndVersion(master.id(), 2).orElseThrow().status())
            .isEqualTo(SignKeyVersionStatus.COMPROMISED);
        assertThat(versions.findBySignKeyIdAndVersion(master.id(), 3).orElseThrow().status())
            .isEqualTo(SignKeyVersionStatus.ACTIVE);
    }

    @Test
    void ACTIVE_가_아닌_마스터_진행_중_작업_키_관리자_부재는_시작_전에_거부한다() {
        assertThatThrownBy(() -> service(null).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);

        operations.insert(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null, REQUESTER,
            null, "OTHER", NOW));
        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        operations.rows.clear();

        keys.update(master.suspend("점검", NOW), master.versionNo());
        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        assertThat(keyAdmin.calls).isEmpty();
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedAt()).isNull();
    }

    @Test
    void 기관이_없으면_NOT_FOUND_다() {
        when(institutions.findById(INSTITUTION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.NOT_FOUND);
        assertThat(keyAdmin.calls).isEmpty();
    }

    @Test
    void 대상_목적의_현재_마스터가_없으면_NOT_FOUND_다() {
        Rotate noMaster = new Rotate(INSTITUTION, SignKeyPurpose.STATUS_LIST, REQUESTER, approval.id(), KEY);

        assertThatThrownBy(() -> service(keyAdmin).rotate(noMaster))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.NOT_FOUND);
        assertThat(keyAdmin.calls).isEmpty();
    }

    @Test
    void 같은_멱등키가_이미_쓰였으면_OpenBao_를_부르지_않고_IDEMPOTENCY_CONFLICT_다() {
        operations.insert(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null, REQUESTER,
            null, KEY, NOW).unconfirmed(NOW).failed("old", NOW));

        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.IDEMPOTENCY_CONFLICT);
        assertThat(keyAdmin.calls).isEmpty();
    }

    @Test
    void 호출1_전_재확인이_0행이면_rotate_를_부르지_않고_STATE_CONFLICT_다() {
        operations.reconfirmRejected = 1;

        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
                assertThat(failure.operationId()).isNotNull();
            });
        assertThat(keyAdmin.calls).isEmpty();
    }

    @Test
    void 회전_호출이_거부되면_작업을_FAILED_로_남기고_소유권을_해제한다() {
        keyAdmin.rotateFailure = TransitKeyAdminException.Reason.REJECTED;
        keyAdmin.rotateDespiteFailure = false;

        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
                KmsOperation operation = operations.findById(failure.operationId()).orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.FAILED);
                assertThat(operations.rows.get(operation.id()).owner()).isNull();
            });
        assertThat(keyAdmin.calls).containsExactly("rotate education-sign/" + master.keyName());
    }

    @Test
    void 재조회_자체가_실패하면_UNCONFIRMED_로_남긴다() {
        keyAdmin.readFailure = true;

        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
                KmsOperation operation = operations.findById(failure.operationId()).orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
            });
        assertThat(keyAdmin.calls).containsExactly("rotate education-sign/" + master.keyName(),
            "read education-sign/" + master.keyName());
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
    }

    @Test
    void 회전_호출_중_마스터가_바뀌면_T2_에서_UNCONFIRMED_로_남기고_관측만_기록한다() {
        keyAdmin.onRotate = () -> keys.update(
            keys.findById(master.id()).orElseThrow().suspend("점검", NOW), master.versionNo());

        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
                KmsOperation operation = operations.findById(failure.operationId()).orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
                assertThat(operation.observedVersionAfter()).isEqualTo(3);
            });
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
        assertThat(versions.findBySignKeyIdAndVersion(master.id(), 3)).isEmpty();
        assertThat(keyAdmin.calls).noneMatch(call -> call.startsWith("config"));
    }

    @Test
    void T2_에서_마스터_갱신이_0행이면_STATE_CONFLICT_와_UNCONFIRMED_관측을_남긴다() {
        keys.updateConflict = true;

        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
                KmsOperation operation = operations.findById(failure.operationId()).orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
                assertThat(operation.observedVersionAfter()).isEqualTo(3);
            });
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
        assertThat(versions.findBySignKeyIdAndVersion(master.id(), 3)).isEmpty();
        assertThat(keyAdmin.calls).noneMatch(call -> call.startsWith("config"));
    }

    @Test
    void 호출2_전_재확인이_0행이면_STATE_CONFLICT_이고_config_는_불리지_않고_작업은_RECORDED_로_남는다() {
        operations.reconfirmRejected = 2;

        assertThatThrownBy(() -> service(keyAdmin).rotate(command(approval.id(), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
                KmsOperation operation = operations.findById(failure.operationId()).orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.RECORDED);
            });
        assertThat(keyAdmin.calls).noneMatch(call -> call.startsWith("config"));
    }

    /** §12.2 조건을 메모리에서 흉내 낸 승인 저장소. Task 6 시험이 재사용한다. */
    static final class InMemoryApprovals implements KmsApprovalRepository {
        final Map<UUID, KmsApproval> rows = new LinkedHashMap<>();

        @Override public void insert(KmsApproval approval) { rows.put(approval.id(), approval); }
        @Override public Optional<KmsApproval> findById(UUID id) { return Optional.ofNullable(rows.get(id)); }
        @Override public boolean consume(KmsApprovalConsumption c) {
            KmsApproval a = rows.get(c.approvalId());
            if (a == null || a.consumedAt() != null || !a.expiresAt().isAfter(c.now())
                || !a.institutionId().equals(c.institutionId()) || a.purpose() != c.purpose()
                || a.action() != c.action() || a.approvedBy().equals(c.requester())
                || !a.signKeyId().equals(c.signKeyId())
                || a.expectedVersion() == null || a.expectedVersion() != c.currentVersion()
                || !java.util.Objects.equals(a.targetKeyVersion(), c.targetKeyVersion())
                || !java.util.Objects.equals(a.targetCompromisedAt(), c.targetCompromisedAt())
                || !a.paramsHash().equals(c.paramsHash())) {
                return false;
            }
            rows.put(a.id(), new KmsApproval(a.id(), a.institutionId(), a.purpose(), a.action(), a.signKeyId(),
                a.expectedVersion(), a.targetKeyVersion(), a.targetCompromisedAt(), a.paramsHash(), a.approvedBy(),
                a.expiresAt(), c.now(), c.operationId(), a.createdAt()));
            return true;
        }
        @Override public List<KmsApproval> findOpenByInstitutionId(UUID institutionId, Instant now) {
            return rows.values().stream().filter(a -> a.institutionId().equals(institutionId) && a.isOpen(now))
                .toList();
        }
    }
}
