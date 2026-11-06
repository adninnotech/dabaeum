package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.application.SigningKeyCommands.Compromise;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.FakeKeyAdmin;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryKeys;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryOperations;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryVersions;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.RollbackAwareTransactions;
import com.adn.dabaeum.credential.application.SigningKeyRotationServiceTest.InMemoryApprovals;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsApproval;
import com.adn.dabaeum.credential.domain.KmsApprovalParams;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationStatus;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class SigningKeyCompromiseServiceTest {

    static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
    static final Instant INCIDENT = Instant.parse("2026-09-13T12:00:00Z");
    static final UUID INSTITUTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID REQUESTER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID APPROVER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    static final String KEY = "COMPROMISE-2027";

    private final InMemoryKeys keys = new InMemoryKeys();
    private final InMemoryVersions versions = new InMemoryVersions();
    private final InMemoryOperations operations = new InMemoryOperations();
    private final InMemoryApprovals approvals = new InMemoryApprovals();
    private final FakeKeyAdmin keyAdmin = new FakeKeyAdmin();
    private final InstitutionRepository institutions = mock(InstitutionRepository.class);
    private final UserRoleRepository userRoles = mock(UserRoleRepository.class);
    private InstitutionSignKey master;

    @BeforeEach
    void keyAtVersion2() {
        when(institutions.findById(INSTITUTION)).thenReturn(Optional.of(mock(Institution.class)));
        when(userRoles.findByUserId(APPROVER)).thenReturn(List.of(new UserRoleAssignment(UUID.randomUUID(),
            APPROVER, null, UserRole.PLATFORM_ADMIN, NOW)));
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

    private KmsApproval approve(int keyVersion, Instant compromisedAt, String idempotencyKey) {
        KmsApproval approval = KmsApproval.create(UUID.randomUUID(), master, KmsAction.COMPROMISE, keyVersion,
            compromisedAt, KmsApprovalParams.hash(KmsAction.COMPROMISE, SignKeyPurpose.CREDENTIAL, idempotencyKey),
            APPROVER, NOW);
        approvals.insert(approval);
        return approval;
    }

    private SigningKeyCompromiseService service(TransitKeyAdminPort port) {
        PlatformTransactionManager transactions = new RollbackAwareTransactions(keys.rows, versions.rows,
            operations.rows, approvals.rows);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ApproverRoleCheck roles = new ApproverRoleCheck(userRoles);
        KmsProcessIdentity identity = new KmsProcessIdentity("host-test");
        SigningKeyRotationService rotation = new SigningKeyRotationService(institutions, keys, versions, operations,
            approvals, roles, port, SigningKeyProvisioningServiceTest.JWKS, identity, transactions, clock);
        return new SigningKeyCompromiseService(institutions, keys, versions, operations, approvals, roles, port,
            rotation, identity, transactions, clock);
    }

    private Compromise command(UUID approvalId, int keyVersion, Instant compromisedAt, String idempotencyKey) {
        return new Compromise(INSTITUTION, SignKeyPurpose.CREDENTIAL, REQUESTER, approvalId, keyVersion,
            compromisedAt, idempotencyKey, "노트북 분실");
    }

    private InstitutionSignKeyVersion version(int keyVersion) {
        return versions.findBySignKeyIdAndVersion(master.id(), keyVersion).orElseThrow();
    }

    @Test
    void 현재_버전_유출은_즉시_동작을_커밋하고_같은_요청에서_후속_회전을_이어간다() {
        KmsApproval approval = approve(2, INCIDENT, KEY);

        KmsOperation result = service(keyAdmin).compromise(command(approval.id(), 2, INCIDENT, KEY));

        assertThat(result.action()).isEqualTo(KmsAction.COMPROMISE);
        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(result.targetKeyVersion()).isEqualTo(2);
        assertThat(result.expectedVersionBefore()).isEqualTo(2);
        assertThat(result.observedVersionAfter()).isEqualTo(3);
        assertThat(keyAdmin.calls).containsExactly(
            "rotate education-sign/" + master.keyName(),
            "read education-sign/" + master.keyName(),
            "config education-sign/" + master.keyName() + " 3");
        assertThat(version(2).status()).isEqualTo(SignKeyVersionStatus.COMPROMISED);
        assertThat(version(2).compromisedAt()).isEqualTo(INCIDENT);
        assertThat(version(3).status()).isEqualTo(SignKeyVersionStatus.ACTIVE);
        InstitutionSignKey rotated = keys.findById(master.id()).orElseThrow();
        assertThat(rotated.currentVersion()).isEqualTo(3);
        assertThat(rotated.status()).isEqualTo(SignKeyStatus.ACTIVE);   // 버전 유출 ≠ 마스터 COMPROMISED (§6.5)
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedByOperationId()).isEqualTo(result.id());
        assertThat(operations.rows.get(result.id()).owner()).isNull();
    }

    @Test
    void 과거_버전_유출은_후속_작업_없이_COMPLETED_작업_하나만_남기고_OpenBao_를_부르지_않는다() {
        KmsApproval approval = approve(1, INCIDENT, KEY);

        KmsOperation result = service(keyAdmin).compromise(command(approval.id(), 1, INCIDENT, KEY));

        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(result.targetKeyVersion()).isEqualTo(1);
        assertThat(result.reason()).startsWith("노트북 분실").contains("past version");
        assertThat(result.beforeValue()).contains("\"status\":\"RETIRED\"");
        assertThat(result.afterValue()).contains("\"status\":\"COMPROMISED\"");
        assertThat(keyAdmin.calls).isEmpty();
        assertThat(version(1).status()).isEqualTo(SignKeyVersionStatus.COMPROMISED);
        assertThat(version(2).status()).isEqualTo(SignKeyVersionStatus.ACTIVE);
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
        assertThat(operations.findInProgressBySignKeyId(master.id())).isEmpty();
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedByOperationId()).isEqualTo(result.id());
    }

    @Test
    void 진행_중_작업이_있어도_즉시_동작은_커밋되고_후속_회전은_미룬다() {
        KmsOperation stuck = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            REQUESTER, UUID.randomUUID(), "ROTATE-STUCK", NOW.minusSeconds(600)).unconfirmed(NOW.minusSeconds(590));
        operations.insert(stuck);
        KmsApproval approval = approve(2, INCIDENT, KEY);

        KmsOperation result = service(keyAdmin).compromise(command(approval.id(), 2, INCIDENT, KEY));

        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(result.reason()).contains("deferred: operation " + stuck.id());
        assertThat(keyAdmin.calls).isEmpty();
        assertThat(version(2).status()).isEqualTo(SignKeyVersionStatus.COMPROMISED);
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
        assertThat(operations.findById(stuck.id()).orElseThrow().status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedByOperationId()).isEqualTo(result.id());
        assertThat(operations.rows).hasSize(2);
    }

    @Test
    void 승인의_대상_버전이나_사고_시각이_다르면_거부하고_버전을_바꾸지_않는다() {
        KmsApproval approval = approve(1, INCIDENT, KEY);

        assertThatThrownBy(() -> service(keyAdmin).compromise(command(approval.id(), 2, INCIDENT, KEY)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.APPROVAL_REJECTED);
        assertThatThrownBy(() -> service(keyAdmin).compromise(
                command(approval.id(), 1, INCIDENT.minusSeconds(3600), KEY)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.APPROVAL_REJECTED);
        assertThatThrownBy(() -> service(keyAdmin).compromise(command(approval.id(), 1, INCIDENT, "OTHER")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.APPROVAL_REJECTED);

        assertThat(version(1).status()).isEqualTo(SignKeyVersionStatus.RETIRED);
        assertThat(version(2).status()).isEqualTo(SignKeyVersionStatus.ACTIVE);
        assertThat(operations.rows).isEmpty();
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedAt()).isNull();
        assertThat(keyAdmin.calls).isEmpty();
    }

    @Test
    void 후속_회전이_실패해도_유출_선언은_유지되고_재호출하지_않는다() {
        keyAdmin.rotateFailure = TransitKeyAdminException.Reason.UNAVAILABLE;
        KmsApproval approval = approve(2, INCIDENT, KEY);

        assertThatThrownBy(() -> service(keyAdmin).compromise(command(approval.id(), 2, INCIDENT, KEY)))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
                KmsOperation operation = operations.findById(failure.operationId()).orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
                assertThat(operation.action()).isEqualTo(KmsAction.COMPROMISE);
            });

        assertThat(keyAdmin.calls).containsExactly("rotate education-sign/" + master.keyName());
        assertThat(version(2).status()).isEqualTo(SignKeyVersionStatus.COMPROMISED);
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedAt()).isNotNull();
    }

    @Test
    void 이미_COMPROMISED_인_버전은_STATE_CONFLICT_이고_승인을_소비하지_않는다() {
        versions.compromise(version(1).id(), INCIDENT.minusSeconds(86400));
        KmsApproval approval = approve(1, INCIDENT, KEY);

        assertThatThrownBy(() -> service(keyAdmin).compromise(command(approval.id(), 1, INCIDENT, KEY)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);

        assertThat(version(1).compromisedAt()).isEqualTo(INCIDENT.minusSeconds(86400));
        assertThat(operations.rows).isEmpty();
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedAt()).isNull();
    }

    @Test
    void 키_관리자_포트가_없으면_즉시_동작만_하고_후속_회전을_만들지_않는다() {
        KmsApproval approval = approve(2, INCIDENT, KEY);

        KmsOperation result = service(null).compromise(command(approval.id(), 2, INCIDENT, KEY));

        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(result.reason()).contains("not configured");
        assertThat(version(2).status()).isEqualTo(SignKeyVersionStatus.COMPROMISED);
        assertThat(keys.findById(master.id()).orElseThrow().currentVersion()).isEqualTo(2);
        assertThat(operations.findInProgressBySignKeyId(master.id())).isEmpty();
    }

    @Test
    void 같은_멱등키의_두_번째_선언은_재시도하지_않고_IDEMPOTENCY_CONFLICT_로_거부한다() {
        KmsApproval firstApproval = approve(1, INCIDENT, KEY);
        service(keyAdmin).compromise(command(firstApproval.id(), 1, INCIDENT, KEY));
        KmsApproval secondApproval = approve(2, INCIDENT, KEY);

        assertThatThrownBy(() -> service(keyAdmin).compromise(command(secondApproval.id(), 2, INCIDENT, KEY)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.IDEMPOTENCY_CONFLICT);

        assertThat(version(2).status()).isEqualTo(SignKeyVersionStatus.ACTIVE);
        assertThat(operations.rows).hasSize(1);
        assertThat(keyAdmin.calls).isEmpty();
    }

    @Test
    void 정지된_마스터의_현재_버전_유출은_즉시_동작만_하고_사유에_이유를_남긴다() {
        keys.update(master.suspend("감사", NOW), master.versionNo());
        KmsApproval approval = approve(2, INCIDENT, KEY);

        KmsOperation result = service(keyAdmin).compromise(command(approval.id(), 2, INCIDENT, KEY));

        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(result.reason()).contains("master is SUSPENDED");
        assertThat(keyAdmin.calls).isEmpty();
        assertThat(version(2).status()).isEqualTo(SignKeyVersionStatus.COMPROMISED);
        assertThat(keys.findById(master.id()).orElseThrow().status()).isEqualTo(SignKeyStatus.SUSPENDED);
    }
}
