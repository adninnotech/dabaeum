package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.application.SigningKeyCommands.CreateKeys;
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
import com.adn.dabaeum.credential.domain.SignKeyNaming;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyStatus;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.credential.domain.TransitKeyAdminException;
import com.adn.dabaeum.credential.domain.TransitKeyAdminPort;
import com.adn.dabaeum.credential.domain.TransitKeyInfo;
import com.adn.dabaeum.credential.infrastructure.crypto.Ed25519PublicJwkFactory;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class SigningKeyProvisioningServiceTest {

    static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    static final UUID INSTITUTION = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID REQUESTER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final CredentialPublicJwkFactory JWKS = new Ed25519PublicJwkFactory();

    private final InMemoryKeys keys = new InMemoryKeys();
    private final InMemoryVersions versions = new InMemoryVersions();
    private final InMemoryOperations operations = new InMemoryOperations();
    private final FakeKeyAdmin keyAdmin = new FakeKeyAdmin();
    private final InstitutionRepository institutions = mock(InstitutionRepository.class);

    private SigningKeyProvisioningService service(TransitKeyAdminPort port) {
        when(institutions.findById(INSTITUTION)).thenReturn(Optional.of(mock(Institution.class)));
        return new SigningKeyProvisioningService(institutions, keys, versions, operations, port, JWKS,
            new KmsProcessIdentity("host-test"), new RollbackAwareTransactions(keys.rows, versions.rows,
                operations.rows), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CreateKeys command(String idempotencyKey) {
        return new CreateKeys(INSTITUTION, REQUESTER, idempotencyKey, "기관 개설");
    }

    @Test
    void 두_mount_에_type_만_보내_키를_만들고_재조회_검증_뒤_v1_ACTIVE_로_올린다() {
        List<KmsOperation> result = service(keyAdmin).createKeys(command("CREATE-1"));

        assertThat(keyAdmin.calls).containsExactly(
            "create education-sign/" + SignKeyNaming.keyName(INSTITUTION, 1),
            "read education-sign/" + SignKeyNaming.keyName(INSTITUTION, 1),
            "create education-status/" + SignKeyNaming.keyName(INSTITUTION, 1),
            "read education-status/" + SignKeyNaming.keyName(INSTITUTION, 1));
        for (SignKeyPurpose purpose : SignKeyPurpose.values()) {
            InstitutionSignKey master = keys.findCurrent(INSTITUTION, purpose).orElseThrow();
            assertThat(master.status()).isEqualTo(SignKeyStatus.ACTIVE);
            assertThat(master.currentVersion()).isEqualTo(1);
            assertThat(master.canSign()).isTrue();
            InstitutionSignKeyVersion version = versions.findBySignKeyIdAndVersion(master.id(), 1).orElseThrow();
            assertThat(version.status()).isEqualTo(SignKeyVersionStatus.ACTIVE);
            assertThat(version.kidFragment()).isEqualTo(SignKeyNaming.kidFragment(purpose, 1, 1));
            assertThat(version.createdBy()).isEqualTo(REQUESTER);
            assertThat(version.openbaoCreatedAt()).isEqualTo(FakeKeyAdmin.CREATED_AT);
            assertThat(JWKS.publicKey(version.publicKeyJwk()).getEncoded())
                .isEqualTo(keyAdmin.publicKey(purpose.mount(), master.keyName(), 1).getEncoded());
        }
        assertThat(result).hasSize(2)
            .allSatisfy(operation -> {
                assertThat(operation.action()).isEqualTo(KmsAction.CREATE);
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.COMPLETED);
                assertThat(operation.observedVersionAfter()).isEqualTo(1);
                assertThat(operations.rows.get(operation.id()).owner()).isNull();
                assertThat(operation.afterValue()).contains("\"status\":\"ACTIVE\"");
            });
        assertThat(result).extracting(KmsOperation::idempotencyKey)
            .containsExactly("CREATE-1/credential", "CREATE-1/status-list");
        assertThat(operations.findInProgressBySignKeyId(result.get(0).signKeyId())).isEmpty();
    }

    @Test
    void 백필이_만든_PROVISIONING_마스터는_새로_만들지_않고_그_행을_올린다() {
        InstitutionSignKey backfilled = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.CREDENTIAL, 1, NOW.minusSeconds(86400));
        keys.insert(backfilled);

        service(keyAdmin).createKeys(command("CREATE-2"));

        InstitutionSignKey master = keys.findCurrent(INSTITUTION, SignKeyPurpose.CREDENTIAL).orElseThrow();
        assertThat(master.id()).isEqualTo(backfilled.id());
        assertThat(master.status()).isEqualTo(SignKeyStatus.ACTIVE);
        assertThat(keys.rows).hasSize(2);
    }

    @Test
    void 두_목적이_이미_있으면_ALREADY_EXISTS_이고_한쪽만_있으면_나머지만_만든다() {
        service(keyAdmin).createKeys(command("CREATE-3"));
        keyAdmin.calls.clear();

        assertThatThrownBy(() -> service(keyAdmin).createKeys(command("CREATE-4")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.ALREADY_EXISTS);
        assertThat(keyAdmin.calls).isEmpty();

        InMemoryKeys partial = new InMemoryKeys();
        partial.insert(keys.findCurrent(INSTITUTION, SignKeyPurpose.CREDENTIAL).orElseThrow());
        InMemoryOperations partialOperations = new InMemoryOperations();
        SigningKeyProvisioningService service = new SigningKeyProvisioningService(institutions, partial, versions,
            partialOperations, keyAdmin, JWKS, new KmsProcessIdentity("host-test"),
            new RollbackAwareTransactions(partial.rows, versions.rows, partialOperations.rows),
            Clock.fixed(NOW, ZoneOffset.UTC));
        List<KmsOperation> result = service.createKeys(command("CREATE-5"));
        assertThat(result).extracting(KmsOperation::purpose).containsExactly(SignKeyPurpose.STATUS_LIST);
        assertThat(keyAdmin.calls).allMatch(call -> call.contains("education-status/"));
    }

    @Test
    void 재조회_설정이_설계값이_아니면_ERROR_와_FAILED_로_남기고_다음_목적을_시작하지_않는다() {
        keyAdmin.exportableOnCreate = true;

        assertThatThrownBy(() -> service(keyAdmin).createKeys(command("CREATE-6")))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
                assertThat(failure.operationId()).isNotNull();
                KmsOperation operation = operations.findById(failure.operationId()).orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.FAILED);
                assertThat(operation.reason()).contains("design");
            });
        InstitutionSignKey master = keys.findCurrent(INSTITUTION, SignKeyPurpose.CREDENTIAL).orElseThrow();
        assertThat(master.status()).isEqualTo(SignKeyStatus.ERROR);
        assertThat(versions.rows).isEmpty();
        assertThat(keys.findCurrent(INSTITUTION, SignKeyPurpose.STATUS_LIST)).isEmpty();
        assertThat(keyAdmin.calls).noneMatch(call -> call.contains("education-status/"));
    }

    @Test
    void 생성_호출이_거부되면_FAILED_와_ERROR_로_남기고_재조회하지_않는다() {
        keyAdmin.createFailure = TransitKeyAdminException.Reason.REJECTED;
        keyAdmin.createDespiteFailure = false;

        assertThatThrownBy(() -> service(keyAdmin).createKeys(command("CREATE-14")))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
                assertThat(failure.operationId()).isNotNull();
                KmsOperation operation = operations.findById(failure.operationId()).orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.FAILED);
                assertThat(operation.reason()).contains("rejected");
            });
        InstitutionSignKey master = keys.findCurrent(INSTITUTION, SignKeyPurpose.CREDENTIAL).orElseThrow();
        assertThat(master.status()).isEqualTo(SignKeyStatus.ERROR);
        assertThat(keyAdmin.calls).containsExactly(
            "create education-sign/" + SignKeyNaming.keyName(INSTITUTION, 1));
    }

    @Test
    void 재확인_0행이면_생성_호출_없이_STATE_CONFLICT_다() {
        operations.reconfirmRejected = 1;

        assertThatThrownBy(() -> service(keyAdmin).createKeys(command("CREATE-15")))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
                assertThat(failure.operationId()).isNotNull();
            });
        assertThat(keyAdmin.calls).isEmpty();
    }

    /** 작업 행을 직접 넣고 이 프로세스("host-test")가 소유권을 쥔 상태를 만든다 — T1 을 지난 뒤의 모습. */
    private KmsOperation owned(KmsOperation operation) {
        operations.insert(operation);
        assertThat(operations.acquireOwnership(operation.id(), "host-test", NOW, NOW.plusSeconds(300))).isTrue();
        return operations.findById(operation.id()).orElseThrow();
    }

    @Test
    void 비현재_COMPROMISED_마스터만_있으면_다음_세대를_같은_규칙의_이름과_kid_로_만든다() {
        for (SignKeyPurpose purpose : SignKeyPurpose.values()) {
            keys.insert(InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION, purpose, 1,
                NOW.minusSeconds(86400)).activate(1, NOW.minusSeconds(86400))
                .compromise("openbao rebuilt", NOW.minusSeconds(3600)));
        }

        List<KmsOperation> result = service(keyAdmin).createKeys(command("CREATE-G2"));

        assertThat(keyAdmin.calls).containsExactly(
            "create education-sign/" + SignKeyNaming.keyName(INSTITUTION, 2),
            "read education-sign/" + SignKeyNaming.keyName(INSTITUTION, 2),
            "create education-status/" + SignKeyNaming.keyName(INSTITUTION, 2),
            "read education-status/" + SignKeyNaming.keyName(INSTITUTION, 2));
        for (SignKeyPurpose purpose : SignKeyPurpose.values()) {
            InstitutionSignKey current = keys.findCurrent(INSTITUTION, purpose).orElseThrow();
            assertThat(current.generation()).isEqualTo(2);
            assertThat(current.keyName()).endsWith("-g2");
            assertThat(current.status()).isEqualTo(SignKeyStatus.ACTIVE);
            InstitutionSignKeyVersion v1 = versions.findBySignKeyIdAndVersion(current.id(), 1).orElseThrow();
            assertThat(v1.kidFragment()).isEqualTo(SignKeyNaming.kidFragment(purpose, 2, 1));
        }
        assertThat(keys.rows.values()).filteredOn(key -> key.generation() == 1)
            .allSatisfy(old -> {
                assertThat(old.status()).isEqualTo(SignKeyStatus.COMPROMISED);
                assertThat(old.isCurrent()).isFalse();
            });
        assertThat(result).hasSize(2).allSatisfy(op -> assertThat(op.status()).isEqualTo(KmsOperationStatus.COMPLETED));
    }

    @Test
    void 비현재_마스터에_COMPROMISED_가_아닌_것이_있으면_STATE_CONFLICT_이고_아무_것도_만들지_않는다() {
        InstitutionSignKey activeOld = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.CREDENTIAL, 1, NOW).activate(1, NOW);
        keys.insert(new InstitutionSignKey(activeOld.id(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, false,
            activeOld.mount(), activeOld.keyName(), activeOld.keyType(), 1, SignKeyStatus.REVOKED, "폐지", null, null,
            NOW, null, activeOld.versionNo(), NOW, NOW));   // is_current=false 인 REVOKED — 도메인 전이로는 못 만드는 손상 상태

        assertThatThrownBy(() -> service(keyAdmin).createKeys(command("CREATE-BAD")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        assertThat(keyAdmin.calls).isEmpty();
        assertThat(keys.rows).hasSize(1);
        assertThat(operations.rows).isEmpty();
    }

    @Test
    void ERROR_마스터는_PROVISIONING_으로_되돌려_같은_이름으로_다시_만든다() {
        InstitutionSignKey error = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.CREDENTIAL, 1, NOW.minusSeconds(3600)).markError("key not created", NOW.minusSeconds(3500));
        keys.insert(error);

        service(keyAdmin).createKeys(command("CREATE-RETRY"));

        InstitutionSignKey retried = keys.findById(error.id()).orElseThrow();
        assertThat(retried.status()).isEqualTo(SignKeyStatus.ACTIVE);
        assertThat(retried.keyName()).isEqualTo(error.keyName());
        assertThat(retried.generation()).isEqualTo(1);
        assertThat(retried.versionNo()).isGreaterThan(error.versionNo());
        assertThat(keyAdmin.calls).contains("create education-sign/" + error.keyName());
        assertThat(keys.findCurrent(INSTITUTION, SignKeyPurpose.STATUS_LIST)).isPresent();   // 없던 목적은 새로 만든다
    }

    @Test
    void 판정_재개는_존재하는_키를_검증해_T2_로_끝내고_create_를_다시_부르지_않는다() {
        InstitutionSignKey provisioning = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.CREDENTIAL, 1, NOW);
        keys.insert(provisioning);
        keyAdmin.createKey(provisioning.mount(), provisioning.keyName());   // 원요청의 create 는 착지했다
        keyAdmin.calls.clear();
        KmsOperation unconfirmed = owned(KmsOperation.prepared(UUID.randomUUID(), provisioning, KmsAction.CREATE,
            null, "개설", REQUESTER, null, "CREATE-X/credential", NOW).unconfirmed(NOW));

        KmsOperation result = service(keyAdmin).resume(provisioning, unconfirmed, REQUESTER);

        assertThat(keyAdmin.calls).containsExactly("read education-sign/" + provisioning.keyName());
        assertThat(result.status()).isEqualTo(KmsOperationStatus.COMPLETED);
        assertThat(result.observedVersionAfter()).isEqualTo(1);
        InstitutionSignKey activated = keys.findById(provisioning.id()).orElseThrow();
        assertThat(activated.status()).isEqualTo(SignKeyStatus.ACTIVE);
        assertThat(activated.currentVersion()).isEqualTo(1);
        assertThat(versions.findBySignKeyIdAndVersion(provisioning.id(), 1)).isPresent();
        assertThat(operations.rows.get(result.id()).owner()).isNull();
    }

    @Test
    void 판정_재개_시_키가_없으면_STATE_CONFLICT_이고_아무_것도_바꾸지_않는다() {
        InstitutionSignKey provisioning = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.CREDENTIAL, 1, NOW);
        keys.insert(provisioning);
        KmsOperation unconfirmed = owned(KmsOperation.prepared(UUID.randomUUID(), provisioning, KmsAction.CREATE,
            null, "개설", REQUESTER, null, "CREATE-Y/credential", NOW).unconfirmed(NOW));

        assertThatThrownBy(() -> service(keyAdmin).resume(provisioning, unconfirmed, REQUESTER))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        assertThat(operations.findById(unconfirmed.id()).orElseThrow().status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
        assertThat(keys.findById(provisioning.id()).orElseThrow().status()).isEqualTo(SignKeyStatus.PROVISIONING);
        assertThat(keyAdmin.calls).containsExactly("read education-sign/" + provisioning.keyName());
    }

    @Test
    void 판정_재개_시_설계값이_어긋나면_FAILED_와_ERROR_로_남긴다() {
        InstitutionSignKey provisioning = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.CREDENTIAL, 1, NOW);
        keys.insert(provisioning);
        keyAdmin.exportableOnCreate = true;
        keyAdmin.createKey(provisioning.mount(), provisioning.keyName());   // 키는 있으나 설계값을 어겼다
        keyAdmin.calls.clear();
        KmsOperation unconfirmed = owned(KmsOperation.prepared(UUID.randomUUID(), provisioning, KmsAction.CREATE,
            null, "개설", REQUESTER, null, "CREATE-Z/credential", NOW).unconfirmed(NOW)
            .annotated("[adjudicated RECORDED by op at " + NOW + "] audit ok", NOW));

        assertThatThrownBy(() -> service(keyAdmin).resume(provisioning, unconfirmed, REQUESTER))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
        KmsOperation failed = operations.findById(unconfirmed.id()).orElseThrow();
        assertThat(failed.status()).isEqualTo(KmsOperationStatus.FAILED);
        assertThat(failed.reason()).startsWith("개설");
        assertThat(failed.reason()).contains("[adjudicated RECORDED");
        assertThat(failed.reason()).endsWith("key config differs from design defaults");
        InstitutionSignKey error = keys.findById(provisioning.id()).orElseThrow();
        assertThat(error.status()).isEqualTo(SignKeyStatus.ERROR);
        assertThat(operations.rows.get(unconfirmed.id()).owner()).isNull();
        assertThat(keyAdmin.calls).containsExactly("read education-sign/" + provisioning.keyName());
    }

    @Test
    void 마스터_INSERT_가_동시_생성으로_유니크_충돌하면_STATE_CONFLICT_다() {
        keys.insertConflict = true;

        assertThatThrownBy(() -> service(keyAdmin).createKeys(command("CREATE-17")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        assertThat(keyAdmin.calls).isEmpty();
    }

    @Test
    void 생성_호출이_불확실해도_재조회로_키가_보이면_진행하고_없으면_FAILED_다() {
        keyAdmin.createFailure = TransitKeyAdminException.Reason.UNAVAILABLE;
        keyAdmin.createDespiteFailure = true;

        List<KmsOperation> result = service(keyAdmin).createKeys(command("CREATE-7"));
        assertThat(result).extracting(KmsOperation::status).containsOnly(KmsOperationStatus.COMPLETED);
        assertThat(keys.findCurrent(INSTITUTION, SignKeyPurpose.CREDENTIAL).orElseThrow().status())
            .isEqualTo(SignKeyStatus.ACTIVE);

        InMemoryKeys fresh = new InMemoryKeys();
        InMemoryOperations freshOperations = new InMemoryOperations();
        FakeKeyAdmin absent = new FakeKeyAdmin();
        absent.createFailure = TransitKeyAdminException.Reason.UNAVAILABLE;
        absent.createDespiteFailure = false;
        SigningKeyProvisioningService service = new SigningKeyProvisioningService(institutions, fresh, versions,
            freshOperations, absent, JWKS, new KmsProcessIdentity("host-test"),
            new RollbackAwareTransactions(fresh.rows, versions.rows, freshOperations.rows),
            Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> service.createKeys(command("CREATE-8")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
        assertThat(fresh.findCurrent(INSTITUTION, SignKeyPurpose.CREDENTIAL).orElseThrow().status())
            .isEqualTo(SignKeyStatus.ERROR);
        assertThat(freshOperations.rows.values()).extracting(KmsOperation::status)
            .containsExactly(KmsOperationStatus.FAILED);
    }

    @Test
    void 재조회_자체가_실패하면_UNCONFIRMED_로_남긴다() {
        keyAdmin.createFailure = TransitKeyAdminException.Reason.UNAVAILABLE;
        keyAdmin.createDespiteFailure = true;
        keyAdmin.readFailure = true;

        assertThatThrownBy(() -> service(keyAdmin).createKeys(command("CREATE-9")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
        assertThat(operations.rows.values()).extracting(KmsOperation::status)
            .containsExactly(KmsOperationStatus.UNCONFIRMED);
        assertThat(keys.findCurrent(INSTITUTION, SignKeyPurpose.CREDENTIAL).orElseThrow().status())
            .isEqualTo(SignKeyStatus.PROVISIONING);
    }

    @Test
    void 진행_중_작업이나_같은_멱등키가_있으면_OpenBao_를_부르지_않고_거부한다() {
        InstitutionSignKey provisioning = InstitutionSignKey.provisioning(UUID.randomUUID(), INSTITUTION,
            SignKeyPurpose.CREDENTIAL, 1, NOW);
        keys.insert(provisioning);
        operations.insert(KmsOperation.prepared(UUID.randomUUID(), provisioning, KmsAction.CREATE, null, null,
            REQUESTER, null, "OTHER/credential", NOW));

        assertThatThrownBy(() -> service(keyAdmin).createKeys(command("CREATE-10")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
        assertThat(keyAdmin.calls).isEmpty();
        assertThat(keys.rows).hasSize(1);

        operations.rows.clear();
        operations.insert(KmsOperation.prepared(UUID.randomUUID(), provisioning, KmsAction.CREATE, null, null,
            REQUESTER, null, "CREATE-11/credential", NOW).unconfirmed(NOW).failed("old", NOW));
        assertThatThrownBy(() -> service(keyAdmin).createKeys(command("CREATE-11")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.IDEMPOTENCY_CONFLICT);
        assertThat(keyAdmin.calls).isEmpty();
    }

    @Test
    void T2_에서_마스터_갱신이_0행이면_UNCONFIRMED_와_관측_버전을_남긴다() {
        keys.updateConflict = true;

        assertThatThrownBy(() -> service(keyAdmin).createKeys(command("CREATE-18")))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
                KmsOperation operation = operations.findById(failure.operationId()).orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
                assertThat(operation.observedVersionAfter()).isEqualTo(1);
            });
        assertThat(keys.findCurrent(INSTITUTION, SignKeyPurpose.CREDENTIAL).orElseThrow().status())
            .isEqualTo(SignKeyStatus.PROVISIONING);
        assertThat(versions.rows).isEmpty();
    }

    @Test
    void 이미_UNCONFIRMED_인_작업이_T2_에서_0행이어도_STATE_CONFLICT_와_관측을_남긴다() {
        // 생성 호출이 불확실해 UNCONFIRMED 로 남은 뒤 재조회는 v1 을 확인한 상태에서 T2 가 0행이 되는 경로다.
        keyAdmin.createFailure = TransitKeyAdminException.Reason.UNAVAILABLE;
        keyAdmin.createDespiteFailure = true;
        keys.updateConflict = true;

        assertThatThrownBy(() -> service(keyAdmin).createKeys(command("CREATE-19")))
            .isInstanceOf(SigningKeyException.class)
            .satisfies(exception -> {
                SigningKeyException failure = (SigningKeyException) exception;
                assertThat(failure.kind()).isEqualTo(SigningKeyException.Kind.STATE_CONFLICT);
                KmsOperation operation = operations.findById(failure.operationId()).orElseThrow();
                assertThat(operation.status()).isEqualTo(KmsOperationStatus.UNCONFIRMED);
                assertThat(operation.observedVersionAfter()).isEqualTo(1);
            });
        assertThat(keys.findCurrent(INSTITUTION, SignKeyPurpose.CREDENTIAL).orElseThrow().status())
            .isEqualTo(SignKeyStatus.PROVISIONING);
        assertThat(versions.rows).isEmpty();
    }

    @Test
    void 키_관리자_포트가_없거나_기관이_없으면_DB_를_바꾸지_않는다() {
        assertThatThrownBy(() -> service(null).createKeys(command("CREATE-12")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.KMS_UNAVAILABLE);
        assertThat(keys.rows).isEmpty();
        assertThat(operations.rows).isEmpty();

        SigningKeyProvisioningService service = service(keyAdmin);
        when(institutions.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createKeys(command("CREATE-13")))
            .isInstanceOf(SigningKeyException.class)
            .extracting(exception -> ((SigningKeyException) exception).kind())
            .isEqualTo(SigningKeyException.Kind.NOT_FOUND);
        assertThat(keys.rows).isEmpty();
    }

    /** 가짜 저장소·키 관리자. Task 5·6 시험이 같은 패키지에서 재사용한다. */
    static final class InMemoryKeys implements InstitutionSignKeyRepository {
        final Map<UUID, InstitutionSignKey> rows = new LinkedHashMap<>();
        boolean insertConflict;
        /** 다음 {@link #update} 한 번만 낙관적 잠금과 무관하게 0행으로 만든다 (동시 변경 시뮬레이션). */
        boolean updateConflict;

        @Override public void insert(InstitutionSignKey key) {
            if (insertConflict) {
                insertConflict = false;
                throw new DuplicateKeyException("uq_tb_institution_sign_keys_current");
            }
            rows.put(key.id(), key);
        }
        @Override public Optional<InstitutionSignKey> findById(UUID id) { return Optional.ofNullable(rows.get(id)); }
        @Override public Optional<InstitutionSignKey> findCurrent(UUID institutionId, SignKeyPurpose purpose) {
            return rows.values().stream().filter(key -> key.institutionId().equals(institutionId)
                && key.purpose() == purpose && key.isCurrent()).findFirst();
        }
        @Override public List<InstitutionSignKey> findByInstitutionId(UUID institutionId) {
            return rows.values().stream().filter(key -> key.institutionId().equals(institutionId)).toList();
        }
        @Override public boolean update(InstitutionSignKey key, int expectedVersionNo) {
            if (updateConflict) {
                updateConflict = false;
                return false;
            }
            InstitutionSignKey stored = rows.get(key.id());
            if (stored == null || stored.versionNo() != expectedVersionNo) {
                return false;
            }
            rows.put(key.id(), key);
            return true;
        }
    }

    static final class InMemoryVersions implements InstitutionSignKeyVersionRepository {
        final Map<UUID, InstitutionSignKeyVersion> rows = new LinkedHashMap<>();

        @Override public void insert(InstitutionSignKeyVersion version) { rows.put(version.id(), version); }
        @Override public Optional<InstitutionSignKeyVersion> findById(UUID id) { return Optional.ofNullable(rows.get(id)); }
        @Override public Optional<InstitutionSignKeyVersion> findBySignKeyIdAndVersion(UUID signKeyId, int keyVersion) {
            return rows.values().stream().filter(row -> row.signKeyId().equals(signKeyId)
                && row.keyVersion() == keyVersion).findFirst();
        }
        @Override public Optional<InstitutionSignKeyVersion> findByInstitutionIdAndKidFragment(UUID institutionId,
            String kidFragment) {
            return rows.values().stream().filter(row -> row.institutionId().equals(institutionId)
                && row.kidFragment().equals(kidFragment)).findFirst();
        }
        @Override public List<InstitutionSignKeyVersion> findBySignKeyId(UUID signKeyId) {
            return rows.values().stream().filter(row -> row.signKeyId().equals(signKeyId))
                .sorted((a, b) -> Integer.compare(a.keyVersion(), b.keyVersion())).toList();
        }
        @Override public List<InstitutionSignKeyVersion> findByInstitutionId(UUID institutionId) {
            return rows.values().stream().filter(row -> row.institutionId().equals(institutionId)).toList();
        }
        @Override public boolean retireIfActive(UUID id, Instant retiredAt) {
            InstitutionSignKeyVersion row = rows.get(id);
            if (row == null || row.status() != SignKeyVersionStatus.ACTIVE) {
                return false;
            }
            rows.put(id, row.retire(retiredAt));
            return true;
        }
        @Override public boolean compromise(UUID id, Instant at) {
            InstitutionSignKeyVersion row = rows.get(id);
            if (row == null || row.status() == SignKeyVersionStatus.COMPROMISED) {
                return false;
            }
            rows.put(id, row.compromise(at));
            return true;
        }
        @Override public Optional<SignKeyVersionStatus> findStatusForShare(UUID signKeyId, int keyVersion) {
            return findBySignKeyIdAndVersion(signKeyId, keyVersion).map(InstitutionSignKeyVersion::status);
        }
        @Override public Optional<InstitutionSignKeyVersion> findBySignKeyIdAndVersionForUpdate(UUID signKeyId,
            int keyVersion) {
            return findBySignKeyIdAndVersion(signKeyId, keyVersion);
        }
    }

    static final class InMemoryOperations implements KmsOperationRepository {
        final Map<UUID, KmsOperation> rows = new LinkedHashMap<>();
        /** 0 이면 비활성. N 이면 {@link #reconfirmOwnership} 의 N 번째 호출(1부터)만 0행으로 거부한다. */
        int reconfirmRejected;
        private int reconfirmCallCount;

        @Override public void insert(KmsOperation operation) {
            // uq_tb_kms_operations_in_progress 는 부분 유니크라 진행 중이 아닌 행(예: 즉시 동작의 COMPLETED)은
            // 대상이 아니다 — 새 행 자신이 진행 중일 때만 기존 진행 중 행과 충돌한다 (V22, 계획 6 결정 2).
            if (operation.inProgress() && rows.values().stream().anyMatch(row -> row.signKeyId()
                .equals(operation.signKeyId()) && row.inProgress())) {
                throw new KmsOperationConflictException(KmsOperationConflictException.Kind.IN_PROGRESS);
            }
            if (rows.values().stream().anyMatch(row -> row.idempotencyKey().equals(operation.idempotencyKey()))) {
                throw new KmsOperationConflictException(KmsOperationConflictException.Kind.IDEMPOTENCY);
            }
            rows.put(operation.id(), operation);
        }
        @Override public Optional<KmsOperation> findById(UUID id) { return Optional.ofNullable(rows.get(id)); }
        @Override public Optional<KmsOperation> findInProgressBySignKeyId(UUID signKeyId) {
            return rows.values().stream().filter(row -> row.signKeyId().equals(signKeyId) && row.inProgress())
                .findFirst();
        }
        @Override public List<KmsOperation> findByInstitutionId(UUID institutionId, int limit) {
            return rows.values().stream().filter(row -> row.institutionId().equals(institutionId))
                .limit(limit).toList();
        }
        @Override public List<KmsOperation> findInProgress(int limit) {
            return rows.values().stream().filter(KmsOperation::inProgress)
                .sorted((a, b) -> a.preparedAt().equals(b.preparedAt())
                    ? a.id().compareTo(b.id()) : a.preparedAt().compareTo(b.preparedAt()))
                .limit(limit).toList();
        }
        @Override public boolean acquireOwnership(UUID id, String owner, Instant now, Instant leaseUntil) {
            KmsOperation row = rows.get(id);
            if (row == null || !row.inProgress() || (row.owner() != null && !row.leaseUntil().isBefore(now))) {
                return false;
            }
            rows.put(id, withOwner(row, owner, leaseUntil, row.lastCallAt()));
            return true;
        }
        @Override public boolean reconfirmOwnership(UUID id, String owner, KmsOperationStatus expectedStatus,
            Instant now, Instant leaseUntil) {
            reconfirmCallCount++;
            if (reconfirmRejected == reconfirmCallCount) {
                return false;
            }
            KmsOperation row = rows.get(id);
            if (row == null || !owner.equals(row.owner()) || row.leaseUntil().isBefore(now)
                || row.status() != expectedStatus) {
                return false;
            }
            rows.put(id, withOwner(row, owner, leaseUntil, now));
            return true;
        }
        @Override public boolean update(KmsOperation operation) {
            KmsOperation stored = rows.get(operation.id());
            if (stored == null) {
                return false;
            }
            rows.put(operation.id(), withOwner(operation, stored.owner(), stored.leaseUntil(), stored.lastCallAt()));
            return true;
        }
        @Override public boolean releaseOwnership(UUID id, String owner) {
            KmsOperation row = rows.get(id);
            if (row == null || !owner.equals(row.owner())) {
                return false;
            }
            rows.put(id, withOwner(row, null, null, row.lastCallAt()));
            return true;
        }
        private static KmsOperation withOwner(KmsOperation o, String owner, Instant leaseUntil, Instant lastCallAt) {
            return new KmsOperation(o.id(), o.signKeyId(), o.institutionId(), o.purpose(), o.action(), o.status(),
                o.targetKeyVersion(), o.expectedVersionBefore(), o.observedVersionAfter(), o.preparedAt(), lastCallAt,
                owner, leaseUntil, o.beforeValue(), o.afterValue(), o.reason(), o.requestedBy(), o.approvalId(),
                o.idempotencyKey(), o.createdAt(), o.updatedAt());
        }
    }

    /** 실제 Ed25519 키를 만드는 가짜 Transit. 실패는 필드로 지시한다. */
    static final class FakeKeyAdmin implements TransitKeyAdminPort {
        static final Instant CREATED_AT = Instant.parse("2026-09-11T00:00:01Z");
        final List<String> calls = new ArrayList<>();
        final Map<String, List<PublicKey>> versions = new HashMap<>();
        final Map<String, Integer> minEncryption = new HashMap<>();
        TransitKeyAdminException.Reason createFailure;
        boolean createDespiteFailure;
        TransitKeyAdminException.Reason rotateFailure;
        boolean rotateDespiteFailure;
        int configFailuresRemaining;
        boolean readFailure;
        boolean exportableOnCreate;
        int extraVersionsOnRotate;
        /** rotateKey 호출 시점에 실행된다. 회전 도중 마스터가 동시에 바뀌는 상황을 시험에서 흉내낼 때 쓴다. */
        Runnable onRotate;

        @Override public void createKey(String mount, String keyName) {
            calls.add("create " + mount + "/" + keyName);
            if (createFailure == null || createDespiteFailure) {
                versions.computeIfAbsent(path(mount, keyName), ignored -> new ArrayList<>(List.of(generate())));
            }
            if (createFailure != null) {
                throw new TransitKeyAdminException(createFailure, "create failed", null);
            }
        }
        @Override public Optional<TransitKeyInfo> readKey(String mount, String keyName) {
            calls.add("read " + mount + "/" + keyName);
            if (readFailure) {
                throw new TransitKeyAdminException(TransitKeyAdminException.Reason.UNAVAILABLE, "read failed", null);
            }
            List<PublicKey> keys = versions.get(path(mount, keyName));
            if (keys == null) {
                return Optional.empty();
            }
            Map<Integer, TransitKeyInfo.Version> map = new HashMap<>();
            for (int i = 0; i < keys.size(); i++) {
                map.put(i + 1, new TransitKeyInfo.Version(keys.get(i), CREATED_AT.plusSeconds(i)));
            }
            return Optional.of(new TransitKeyInfo("ed25519", exportableOnCreate, false, false, false, keys.size(),
                minEncryption.getOrDefault(path(mount, keyName), 0), 0, map));
        }
        @Override public void rotateKey(String mount, String keyName) {
            calls.add("rotate " + mount + "/" + keyName);
            if (onRotate != null) {
                onRotate.run();
            }
            if (rotateFailure == null || rotateDespiteFailure) {
                List<PublicKey> keys = versions.get(path(mount, keyName));
                keys.add(generate());
                for (int i = 0; i < extraVersionsOnRotate; i++) {
                    keys.add(generate());
                }
            }
            if (rotateFailure != null) {
                throw new TransitKeyAdminException(rotateFailure, "rotate failed", null);
            }
        }
        @Override public void setMinEncryptionVersion(String mount, String keyName, int minEncryptionVersion) {
            calls.add("config " + mount + "/" + keyName + " " + minEncryptionVersion);
            if (configFailuresRemaining > 0) {
                configFailuresRemaining--;
                throw new TransitKeyAdminException(TransitKeyAdminException.Reason.UNAVAILABLE, "config failed", null);
            }
            minEncryption.put(path(mount, keyName), minEncryptionVersion);
        }
        PublicKey publicKey(String mount, String keyName, int version) {
            return versions.get(path(mount, keyName)).get(version - 1);
        }
        private static String path(String mount, String keyName) { return mount + "/" + keyName; }
        private static PublicKey generate() {
            try {
                return KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }
    }

    /**
     * 트랜잭션 경계를 흉내낸다. {@code transactions.execute} 블록이 던진 예외로 롤백되면, 넘겨받은 가짜
     * 저장소들의 {@code rows} 맵을 트랜잭션 시작 시점 상태로 되돌려 실제 DB 롤백을 재현한다. 커밋되면
     * 스냅샷을 버릴 뿐 아무 것도 하지 않는다. Task 4·5 시험이 공유한다.
     */
    static final class RollbackAwareTransactions implements PlatformTransactionManager {
        private final List<Map<?, ?>> maps;

        RollbackAwareTransactions(Map<?, ?>... maps) {
            this.maps = List.of(maps);
        }

        @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new Snapshot(maps);
        }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { ((Snapshot) status).restore(); }

        private static final class Snapshot extends SimpleTransactionStatus {
            private final List<Map<Object, Object>> originals;
            private final List<Map<Object, Object>> copies;

            @SuppressWarnings("unchecked")
            Snapshot(List<Map<?, ?>> maps) {
                this.originals = maps.stream().map(m -> (Map<Object, Object>) m).toList();
                this.copies = originals.stream().<Map<Object, Object>>map(LinkedHashMap::new).toList();
            }

            void restore() {
                for (int i = 0; i < originals.size(); i++) {
                    originals.get(i).clear();
                    originals.get(i).putAll(copies.get(i));
                }
            }
        }
    }
}
