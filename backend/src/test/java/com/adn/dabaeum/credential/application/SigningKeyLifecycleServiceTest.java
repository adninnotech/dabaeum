package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.application.SigningKeyCommands.CreateApproval;
import com.adn.dabaeum.credential.application.SigningKeyCommands.Resume;
import com.adn.dabaeum.credential.application.SigningKeyCommands.Revoke;
import com.adn.dabaeum.credential.application.SigningKeyCommands.Suspend;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryKeys;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryOperations;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.InMemoryVersions;
import com.adn.dabaeum.credential.application.SigningKeyProvisioningServiceTest.RollbackAwareTransactions;
import com.adn.dabaeum.credential.application.SigningKeyRotationServiceTest.InMemoryApprovals;
import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsApproval;
import com.adn.dabaeum.credential.domain.KmsApprovalParams;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationStatus;
import com.adn.dabaeum.credential.domain.SignKeyNaming;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SigningKeyLifecycleServiceTest {

    static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    static final UUID INSTITUTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID REQUESTER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID APPROVER = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    static final CredentialUriProvider URIS = new DefaultCredentialUriProvider(
        new VcProperties("https://vc.example.test", "v1", "development-1"));

    private final InMemoryKeys keys = new InMemoryKeys();
    private final InMemoryVersions versions = new InMemoryVersions();
    private final InMemoryOperations operations = new InMemoryOperations();
    private final InMemoryApprovals approvals = new InMemoryApprovals();
    private final InstitutionRepository institutions = mock(InstitutionRepository.class);
    private final UserRoleRepository userRoles = mock(UserRoleRepository.class);
    private final SigningKeyLifecycleService service = new SigningKeyLifecycleService(institutions, keys, versions,
        operations, approvals, new ApproverRoleCheck(userRoles), URIS,
        new RollbackAwareTransactions(keys.rows, versions.rows, operations.rows, approvals.rows),
        Clock.fixed(NOW, ZoneOffset.UTC));
    private InstitutionSignKey credential;
    private InstitutionSignKey statusList;

    @BeforeEach
    void activeKeys() throws Exception {
        when(institutions.findById(INSTITUTION)).thenReturn(Optional.of(mock(Institution.class)));
        when(userRoles.findByUserId(APPROVER)).thenReturn(List.of(new UserRoleAssignment(UUID.randomUUID(),
            APPROVER, null, UserRole.PLATFORM_ADMIN, NOW)));
        credential = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, NOW)
            .activate(1, NOW).rotated(2, NOW);
        statusList = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, SignKeyPurpose.STATUS_LIST, 1, NOW)
            .activate(1, NOW);
        keys.insert(credential);
        keys.insert(statusList);
        for (int version = 1; version <= 2; version++) {
            InstitutionSignKeyVersion row = InstitutionSignKeyVersion.active(UUID.randomUUID(), credential.id(),
                INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, version, KmsValues.storedJwk(
                    SigningKeyProvisioningServiceTest.JWKS,
                    KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic()),
                NOW, REQUESTER, NOW.minusSeconds(86400));
            versions.insert(version == 1 ? row.retire(NOW) : row);
        }
        versions.insert(InstitutionSignKeyVersion.active(UUID.randomUUID(), statusList.id(), INSTITUTION,
            SignKeyPurpose.STATUS_LIST, 1, 1, KmsValues.storedJwk(SigningKeyProvisioningServiceTest.JWKS,
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic()), NOW, REQUESTER, NOW));
    }

    private KmsApproval approve(KmsAction action, String idempotencyKey) {
        KmsApproval approval = KmsApproval.create(UUID.randomUUID(), keys.findById(credential.id()).orElseThrow(),
            action, null, null, KmsApprovalParams.hash(action, SignKeyPurpose.CREDENTIAL, idempotencyKey), APPROVER,
            NOW);
        approvals.insert(approval);
        return approval;
    }

    @Test
    void 승인_생성은_현재_버전과_파라미터_해시를_고정하고_COMPROMISE_는_대상_버전을_검증한다() {
        KmsApproval rotate = service.createApproval(new CreateApproval(INSTITUTION, SignKeyPurpose.CREDENTIAL,
            KmsAction.ROTATE, APPROVER, "ROTATE-2027", null, null, "정기 교체"));
        assertThat(rotate.signKeyId()).isEqualTo(credential.id());
        assertThat(rotate.expectedVersion()).isEqualTo(2);
        assertThat(rotate.paramsHash())
            .isEqualTo(KmsApprovalParams.hash(KmsAction.ROTATE, SignKeyPurpose.CREDENTIAL, "ROTATE-2027"));
        assertThat(rotate.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(approvals.findById(rotate.id())).isPresent();

        KmsApproval compromise = service.createApproval(new CreateApproval(INSTITUTION, SignKeyPurpose.CREDENTIAL,
            KmsAction.COMPROMISE, APPROVER, "COMPROMISE-1", 1, NOW.minusSeconds(3600), "유출"));
        assertThat(compromise.targetKeyVersion()).isEqualTo(1);
        assertThat(compromise.targetCompromisedAt()).isEqualTo(NOW.minusSeconds(3600));

        assertThatThrownBy(() -> service.createApproval(new CreateApproval(INSTITUTION, SignKeyPurpose.CREDENTIAL,
            KmsAction.COMPROMISE, APPROVER, "COMPROMISE-2", 9, NOW.minusSeconds(1), null)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.NOT_FOUND);
        assertThatThrownBy(() -> service.createApproval(new CreateApproval(INSTITUTION, SignKeyPurpose.CREDENTIAL,
            KmsAction.COMPROMISE, APPROVER, "COMPROMISE-3", 1, NOW.plusSeconds(1), null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createApproval(new CreateApproval(INSTITUTION, SignKeyPurpose.CREDENTIAL,
            KmsAction.CREATE, APPROVER, "CREATE-1", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createApproval(new CreateApproval(UUID.randomUUID(),
            SignKeyPurpose.CREDENTIAL, KmsAction.ROTATE, APPROVER, "ROTATE-X", null, null, null)))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.NOT_FOUND);
    }

    @Test
    void 정지는_승인_없이_ACTIVE_수료증_키를_SUSPENDED_로_바꾸고_완료_작업을_남긴다() {
        KmsOperation operation = service.suspend(new Suspend(INSTITUTION, REQUESTER, "SUSPEND-1", "감사 지적"));

        InstitutionSignKey suspended = keys.findById(credential.id()).orElseThrow();
        assertThat(suspended.status()).isEqualTo(SignKeyStatus.SUSPENDED);
        assertThat(suspended.statusReason()).isEqualTo("감사 지적");
        assertThat(suspended.suspendedAt()).isEqualTo(NOW);
        assertThat(operation.action()).isEqualTo(KmsAction.SUSPEND);
        assertThat(operation.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(operation.beforeValue()).contains("\"status\":\"ACTIVE\"");
        assertThat(operation.afterValue()).contains("\"status\":\"SUSPENDED\"");
        assertThat(operation.approvalId()).isNull();
        assertThat(keys.findById(statusList.id()).orElseThrow().status()).isEqualTo(SignKeyStatus.ACTIVE);

        assertThatThrownBy(() -> service.suspend(new Suspend(INSTITUTION, REQUESTER, "SUSPEND-2", "다시")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        assertThatThrownBy(() -> service.suspend(new Suspend(INSTITUTION, REQUESTER, "SUSPEND-3", " ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 재개는_승인을_원자_소비하고_거부되면_상태를_바꾸지_않는다() {
        service.suspend(new Suspend(INSTITUTION, REQUESTER, "SUSPEND-1", "감사 지적"));
        KmsApproval approval = approve(KmsAction.RESUME, "RESUME-1");

        assertThatThrownBy(() -> service.resume(new Resume(INSTITUTION, APPROVER, approval.id(), "RESUME-1")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.APPROVAL_REJECTED);
        assertThat(keys.findById(credential.id()).orElseThrow().status()).isEqualTo(SignKeyStatus.SUSPENDED);
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedAt()).isNull();

        KmsOperation operation = service.resume(new Resume(INSTITUTION, REQUESTER, approval.id(), "RESUME-1"));
        assertThat(keys.findById(credential.id()).orElseThrow().status()).isEqualTo(SignKeyStatus.ACTIVE);
        assertThat(operation.action()).isEqualTo(KmsAction.RESUME);
        assertThat(operation.approvalId()).isEqualTo(approval.id());
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedByOperationId()).isEqualTo(operation.id());

        // 다시 ACTIVE 인 상태에서 정지 멱등키를 재사용하면 상태 전이보다 멱등키 유니크가 거부한다.
        assertThatThrownBy(() -> service.suspend(new Suspend(INSTITUTION, REQUESTER, "SUSPEND-1", "중복")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void 폐지는_ACTIVE_나_SUSPENDED_에서_승인을_소비해_REVOKED_로_가고_Status_List_키는_그대로다() {
        service.suspend(new Suspend(INSTITUTION, REQUESTER, "SUSPEND-1", "감사 지적"));
        KmsApproval approval = approve(KmsAction.REVOKE, "REVOKE-1");

        KmsOperation operation = service.revoke(new Revoke(INSTITUTION, REQUESTER, approval.id(), "REVOKE-1", "기관 폐지"));

        InstitutionSignKey revoked = keys.findById(credential.id()).orElseThrow();
        assertThat(revoked.status()).isEqualTo(SignKeyStatus.REVOKED);
        assertThat(revoked.revokedAt()).isEqualTo(NOW);
        assertThat(revoked.statusReason()).isEqualTo("기관 폐지");
        assertThat(operation.afterValue()).contains("\"status\":\"REVOKED\"");
        assertThat(keys.findById(statusList.id()).orElseThrow().canSign()).isTrue();

        KmsApproval again = approve(KmsAction.REVOKE, "REVOKE-2");
        assertThatThrownBy(() -> service.revoke(new Revoke(INSTITUTION, REQUESTER, again.id(), "REVOKE-2", "다시")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        assertThatThrownBy(() -> service.revoke(new Revoke(INSTITUTION, REQUESTER, again.id(), "REVOKE-3", " ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 승인자가_PLATFORM_ADMIN_을_잃었으면_승인을_소비하지_않고_상태도_작업도_남기지_않는다() {
        service.suspend(new Suspend(INSTITUTION, REQUESTER, "SUSPEND-1", "감사 지적"));
        KmsApproval approval = approve(KmsAction.RESUME, "RESUME-ROLE");
        when(userRoles.findByUserId(APPROVER)).thenReturn(List.of());

        assertThatThrownBy(() -> service.resume(new Resume(INSTITUTION, REQUESTER, approval.id(), "RESUME-ROLE")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.APPROVAL_REJECTED);

        assertThat(approvals.findById(approval.id()).orElseThrow().consumedAt()).isNull();
        assertThat(keys.findById(credential.id()).orElseThrow().status()).isEqualTo(SignKeyStatus.SUSPENDED);
        assertThat(operations.rows.values()).noneMatch(operation -> operation.action() == KmsAction.RESUME);
    }

    @Test
    void status_list_목적의_재개_폐지_승인은_400_으로_거부한다() {
        assertThatThrownBy(() -> service.createApproval(new CreateApproval(INSTITUTION, SignKeyPurpose.STATUS_LIST,
            KmsAction.RESUME, APPROVER, "RESUME-SL", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createApproval(new CreateApproval(INSTITUTION, SignKeyPurpose.STATUS_LIST,
            KmsAction.REVOKE, APPROVER, "REVOKE-SL", null, null, null)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(approvals.rows).isEmpty();

        KmsApproval rotate = service.createApproval(new CreateApproval(INSTITUTION, SignKeyPurpose.STATUS_LIST,
            KmsAction.ROTATE, APPROVER, "ROTATE-SL", null, null, null));
        assertThat(rotate.purpose()).isEqualTo(SignKeyPurpose.STATUS_LIST);
    }

    @Test
    void 조회는_마스터_버전_kid_작업_이력_미사용_승인을_한_번에_돌려준다() {
        service.suspend(new Suspend(INSTITUTION, REQUESTER, "SUSPEND-1", "감사 지적"));
        KmsApproval open = approve(KmsAction.RESUME, "RESUME-1");
        approvals.insert(KmsApproval.create(UUID.randomUUID(), keys.findById(credential.id()).orElseThrow(),
            KmsAction.RESUME, null, null, KmsApprovalParams.hash(KmsAction.RESUME, SignKeyPurpose.CREDENTIAL, "OLD"),
            APPROVER, NOW.minus(Duration.ofHours(25))));

        SigningKeyOverview overview = service.overview(INSTITUTION);

        assertThat(overview.institutionId()).isEqualTo(INSTITUTION);
        assertThat(overview.keys()).extracting(view -> view.master().purpose())
            .containsExactly(SignKeyPurpose.CREDENTIAL, SignKeyPurpose.STATUS_LIST);
        SigningKeyOverview.KeyView credentialView = overview.keys().get(0);
        assertThat(credentialView.master().status()).isEqualTo(SignKeyStatus.SUSPENDED);
        assertThat(credentialView.versions()).extracting(view -> view.version().keyVersion()).containsExactly(1, 2);
        assertThat(credentialView.versions().get(1).kid()).isEqualTo(URIS.keyId(INSTITUTION, "v2"));
        assertThat(overview.keys().get(1).versions().get(0).kid()).isEqualTo(URIS.keyId(INSTITUTION, "status-v1"));
        assertThat(overview.operations()).extracting(KmsOperation::action).containsExactly(KmsAction.SUSPEND);
        assertThat(overview.openApprovals()).extracting(KmsApproval::id).containsExactly(open.id());

        when(institutions.findById(INSTITUTION)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.overview(INSTITUTION))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.NOT_FOUND);
    }

    @Test
    void 키가_없는_기관의_조회는_빈_목록이다() {
        UUID empty = UUID.randomUUID();
        when(institutions.findById(empty)).thenReturn(Optional.of(mock(Institution.class)));

        SigningKeyOverview overview = service.overview(empty);

        assertThat(overview.keys()).isEmpty();
        assertThat(overview.operations()).isEmpty();
        assertThat(overview.openApprovals()).isEmpty();
    }

    @Test
    void 마스터_갱신이_동시_변경으로_0행이면_STATE_CONFLICT_이고_작업_INSERT와_승인_소비도_롤백된다() {
        keys.updateConflict = true;

        assertThatThrownBy(() -> service.suspend(new Suspend(INSTITUTION, REQUESTER, "SUSPEND-CONFLICT", "감사 지적")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        assertThat(operations.rows.values()).noneMatch(operation -> operation.action() == KmsAction.SUSPEND);
        assertThat(keys.findById(credential.id()).orElseThrow().status()).isEqualTo(SignKeyStatus.ACTIVE);

        service.suspend(new Suspend(INSTITUTION, REQUESTER, "SUSPEND-1", "감사 지적"));
        KmsApproval approval = approve(KmsAction.RESUME, "RESUME-CONFLICT");
        keys.updateConflict = true;

        assertThatThrownBy(() -> service.resume(new Resume(INSTITUTION, REQUESTER, approval.id(), "RESUME-CONFLICT")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        assertThat(approvals.findById(approval.id()).orElseThrow().consumedAt()).isNull();
        assertThat(keys.findById(credential.id()).orElseThrow().status()).isEqualTo(SignKeyStatus.SUSPENDED);
    }

    @Test
    void COMPROMISE_승인의_compromisedAt_은_대상_버전의_activatedAt_보다_이르면_400_이다() {
        InstitutionSignKeyVersion target = versions.findBySignKeyIdAndVersion(credential.id(), 1).orElseThrow();
        CreateApproval tooEarly = new CreateApproval(INSTITUTION, SignKeyPurpose.CREDENTIAL, KmsAction.COMPROMISE,
            APPROVER, "COMPROMISE-EARLY", 1, target.activatedAt().minusSeconds(1), "유출");

        assertThatThrownBy(() -> service.createApproval(tooEarly)).isInstanceOf(IllegalArgumentException.class);
        assertThat(approvals.rows).isEmpty();

        CreateApproval atActivation = new CreateApproval(INSTITUTION, SignKeyPurpose.CREDENTIAL, KmsAction.COMPROMISE,
            APPROVER, "COMPROMISE-AT", 1, target.activatedAt(), "유출");
        assertThat(service.createApproval(atActivation).targetCompromisedAt()).isEqualTo(target.activatedAt());
    }

    @Test
    void 레거시_버전_0_은_activatedAt_보다_이른_사고_시각도_그대로_받아들인다() throws Exception {
        // 레거시(버전 0)의 activatedAt 은 전환 백필 시각일 뿐 실제 활성화 시각이 아니므로 하한에서 제외된다.
        InstitutionSignKeyVersion legacy = InstitutionSignKeyVersion.legacy(UUID.randomUUID(), credential.id(),
            INSTITUTION, KmsValues.storedJwk(SigningKeyProvisioningServiceTest.JWKS,
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic()), NOW);
        versions.insert(legacy);
        Instant beforeActivation = legacy.activatedAt().minusSeconds(31_536_000);

        KmsApproval approval = service.createApproval(new CreateApproval(INSTITUTION, SignKeyPurpose.CREDENTIAL,
            KmsAction.COMPROMISE, APPROVER, "COMPROMISE-LEGACY", SignKeyNaming.LEGACY_KEY_VERSION, beforeActivation,
            "유출"));

        assertThat(approval.targetKeyVersion()).isEqualTo(SignKeyNaming.LEGACY_KEY_VERSION);
        assertThat(approval.targetCompromisedAt()).isEqualTo(beforeActivation);
    }

    @Test
    void 조회의_작업_이력은_최근_50건으로_제한된다() {
        for (int i = 0; i < 51; i++) {
            KmsOperation operation = KmsOperation.completed(UUID.randomUUID(), credential, KmsAction.SUSPEND, null,
                "사유", REQUESTER, null, "HISTORY-" + i, "{}", "{}", NOW);
            operations.rows.put(operation.id(), operation);
        }

        SigningKeyOverview overview = service.overview(INSTITUTION);

        assertThat(overview.operations()).hasSize(SigningKeyLifecycleService.OPERATION_HISTORY_LIMIT);
    }
}
