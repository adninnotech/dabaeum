package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationConflictException;
import com.adn.dabaeum.credential.domain.KmsOperationRepository;
import com.adn.dabaeum.credential.domain.KmsOperationStatus;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class KmsOperationPersistenceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    static final Instant LEASE = NOW.plusSeconds(300);

    @Autowired
    InstitutionSignKeyRepository keys;

    @Autowired
    KmsOperationRepository operations;

    @Test
    void roundTripsAPreparedOperationAndFindsItInProgress() {
        InstitutionSignKey master = insertActiveMaster();
        UUID requester = insertUser();
        KmsOperation operation = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE,
            null, "정기 교체", requester, null, unique("ROTATE"), NOW);

        operations.insert(operation);

        assertThat(operations.findById(operation.id())).contains(operation);
        assertThat(operations.findInProgressBySignKeyId(master.id())).contains(operation);
        assertThat(operations.findByInstitutionId(master.institutionId(), 10)).containsExactly(operation);
    }

    @Test
    void aSecondInProgressOperationOnTheSameKeyIsRejectedByTheDatabase() {
        InstitutionSignKey master = insertActiveMaster();
        operations.insert(KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE,
            null, null, null, null, unique("ROTATE"), NOW));

        assertThatThrownBy(() -> operations.insert(KmsOperation.prepared(UUID.randomUUID(), master,
            KmsAction.ROTATE, null, null, null, null, unique("ROTATE"), NOW)))
            .isInstanceOf(KmsOperationConflictException.class)
            .extracting(exception -> ((KmsOperationConflictException) exception).kind())
            .isEqualTo(KmsOperationConflictException.Kind.IN_PROGRESS);
    }

    @Test
    void aReusedIdempotencyKeyIsRejectedEvenAfterCompletion() {
        InstitutionSignKey master = insertActiveMaster();
        String key = unique("SUSPEND");
        operations.insert(KmsOperation.completed(UUID.randomUUID(), master, KmsAction.SUSPEND, null, "감사",
            null, null, key, "{\"status\":\"ACTIVE\"}", "{\"status\":\"SUSPENDED\"}", NOW));

        assertThatThrownBy(() -> operations.insert(KmsOperation.prepared(UUID.randomUUID(), master,
            KmsAction.ROTATE, null, null, null, null, key, NOW)))
            .isInstanceOf(KmsOperationConflictException.class)
            .extracting(exception -> ((KmsOperationConflictException) exception).kind())
            .isEqualTo(KmsOperationConflictException.Kind.IDEMPOTENCY);
    }

    @Test
    void ownershipIsAcquiredOnceReconfirmedOnlyByTheOwnerAndReleased() {
        InstitutionSignKey master = insertActiveMaster();
        KmsOperation operation = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE,
            null, null, null, null, unique("ROTATE"), NOW);
        operations.insert(operation);

        assertThat(operations.acquireOwnership(operation.id(), "host-a", NOW, LEASE)).isTrue();
        assertThat(operations.acquireOwnership(operation.id(), "host-b", NOW, LEASE)).isFalse();
        assertThat(operations.reconfirmOwnership(operation.id(), "host-b", KmsOperationStatus.PREPARED,
            NOW.plusSeconds(1), LEASE)).isFalse();
        assertThat(operations.reconfirmOwnership(operation.id(), "host-a", KmsOperationStatus.UNCONFIRMED,
            NOW.plusSeconds(1), LEASE)).isFalse();
        assertThat(operations.reconfirmOwnership(operation.id(), "host-a", KmsOperationStatus.PREPARED,
            NOW.plusSeconds(1), LEASE.plusSeconds(1))).isTrue();

        KmsOperation stored = operations.findById(operation.id()).orElseThrow();
        assertThat(stored.owner()).isEqualTo("host-a");
        assertThat(stored.leaseUntil()).isEqualTo(LEASE.plusSeconds(1));
        assertThat(stored.lastCallAt()).isEqualTo(NOW.plusSeconds(1));

        // lease 가 지나면 같은 프로세스의 다른 스레드가 회수한다 (§11.4). 다른 프로세스 인계는 계획 6.
        assertThat(operations.acquireOwnership(operation.id(), "host-a", LEASE.plusSeconds(2), LEASE.plusSeconds(300)))
            .isTrue();
        assertThat(operations.releaseOwnership(operation.id(), "host-b")).isFalse();
        assertThat(operations.releaseOwnership(operation.id(), "host-a")).isTrue();
        assertThat(operations.findById(operation.id()).orElseThrow().owner()).isNull();
    }

    @Test
    void updateChangesStatusAndValuesButNotOwnership() {
        InstitutionSignKey master = insertActiveMaster();
        KmsOperation operation = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE,
            null, null, null, null, unique("ROTATE"), NOW);
        operations.insert(operation);
        operations.acquireOwnership(operation.id(), "host-a", NOW, LEASE);

        operations.update(operation.unconfirmed(NOW.plusSeconds(1))
            .recorded(4, "{\"currentVersion\":4}", NOW.plusSeconds(2)));

        KmsOperation stored = operations.findById(operation.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(KmsOperationStatus.RECORDED);
        assertThat(stored.observedVersionAfter()).isEqualTo(4);
        assertThat(stored.afterValue()).contains("\"currentVersion\"").contains("4");
        assertThat(stored.owner()).isEqualTo("host-a");
        assertThat(stored.updatedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(operations.findInProgressBySignKeyId(master.id())).isPresent();

        operations.update(stored.completed(NOW.plusSeconds(3)));
        assertThat(operations.findInProgressBySignKeyId(master.id())).isEmpty();
    }

    @Test
    void 진행_중_작업_스캔은_prepared_at_오름차순으로_limit_까지만_돌려주고_완료_작업은_빼준다() {
        // 수료증 마스터 A, Status List 마스터 B (각각 다른 sign_key_id 라 진행 중 유니크에 걸리지 않는다)
        UUID institutionId = insertInstitution();
        InstitutionSignKey masterA = insertActiveMaster(institutionId, SignKeyPurpose.CREDENTIAL);
        InstitutionSignKey masterB = insertActiveMaster(institutionId, SignKeyPurpose.STATUS_LIST);
        UUID requester = insertUser();

        KmsOperation older = KmsOperation.prepared(UUID.randomUUID(), masterA, KmsAction.ROTATE, null, null,
            requester, null, unique("SCAN"), NOW.minusSeconds(60));
        KmsOperation newer = KmsOperation.prepared(UUID.randomUUID(), masterB, KmsAction.ROTATE, null, null,
            requester, null, unique("SCAN"), NOW).unconfirmed(NOW);
        operations.insert(older);
        operations.insert(newer);
        operations.update(older.recorded(2, "{}", NOW).completed(NOW));   // 완료 → 스캔 대상 아님
        KmsOperation youngest = KmsOperation.prepared(UUID.randomUUID(), masterA, KmsAction.ROTATE, null, null,
            requester, null, unique("SCAN"), NOW.plusSeconds(60));
        operations.insert(youngest);

        // findInProgress 는 institutionId 로 거르지 않는 전역 스캔이라 다른 테스트가 남긴 진행 중 작업이
        // 섞일 수 있다. 이 테스트가 만든 행만 골라 순서를 확인한다 (계획 6 최종 리뷰 Fix 3).
        List<KmsOperation> all = operations.findInProgress(10);
        assertThat(all).filteredOn(row -> row.institutionId().equals(institutionId))
            .extracting(KmsOperation::id).containsExactly(newer.id(), youngest.id());
        // limit=1 이 어떤 기관의 어느 행을 돌려주는지는 다른 테스트가 남긴 데이터에 좌우될 수 있으므로,
        // 여기서는 limit 자체가 지켜지는지만 확인한다.
        assertThat(operations.findInProgress(1)).hasSize(1);
    }

    InstitutionSignKey insertActiveMaster() {
        return insertActiveMaster(insertInstitution(), SignKeyPurpose.CREDENTIAL);
    }

    InstitutionSignKey insertActiveMaster(UUID institutionId, SignKeyPurpose purpose) {
        InstitutionSignKey key = InstitutionSignKey.provisioning(
            UUID.randomUUID(), institutionId, purpose, 1, NOW).activate(3, NOW);
        keys.insert(key);
        return key;
    }

    UUID insertInstitution() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, unique("inst"), unique("inst-name"));
        return institutionId;
    }

    UUID insertUser() {
        UUID id = UUID.randomUUID();
        String code = unique("user");
        jdbcTemplate.update("INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            id, code, code + "@example.test");
        return id;
    }

    static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
