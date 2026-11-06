package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.KmsAction;
import com.adn.dabaeum.credential.domain.KmsApproval;
import com.adn.dabaeum.credential.domain.KmsApprovalConsumption;
import com.adn.dabaeum.credential.domain.KmsApprovalParams;
import com.adn.dabaeum.credential.domain.KmsApprovalRepository;
import com.adn.dabaeum.credential.domain.KmsOperation;
import com.adn.dabaeum.credential.domain.KmsOperationRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class KmsApprovalPersistenceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");

    @Autowired
    InstitutionSignKeyRepository keys;

    @Autowired
    KmsOperationRepository operations;

    @Autowired
    KmsApprovalRepository approvals;

    @Test
    void roundTripsAnApprovalAndListsOnlyOpenOnes() {
        InstitutionSignKey master = insertActiveMaster();
        UUID approver = insertUser();
        KmsApproval open = KmsApproval.create(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            hash(KmsAction.ROTATE, "ROTATE-A"), approver, NOW);
        KmsApproval expired = KmsApproval.create(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            hash(KmsAction.ROTATE, "ROTATE-B"), approver, NOW.minus(Duration.ofHours(25)));
        approvals.insert(open);
        approvals.insert(expired);

        assertThat(approvals.findById(open.id())).contains(open);
        assertThat(approvals.findOpenByInstitutionId(master.institutionId(), NOW)).containsExactly(open);
    }

    @Test
    void consumeUpdatesExactlyOneRowOnceAndRecordsTheOperation() {
        InstitutionSignKey master = insertActiveMaster();
        UUID approver = insertUser();
        UUID requester = insertUser();
        String key = "ROTATE-" + UUID.randomUUID();
        KmsApproval approval = KmsApproval.create(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            hash(KmsAction.ROTATE, key), approver, NOW);
        approvals.insert(approval);
        KmsOperation operation = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            requester, approval.id(), key, NOW);
        operations.insert(operation);

        KmsApprovalConsumption consumption = consumption(approval, master, requester, key, operation.id());
        assertThat(approvals.consume(consumption)).isTrue();
        assertThat(approvals.consume(consumption)).isFalse();

        KmsApproval stored = approvals.findById(approval.id()).orElseThrow();
        assertThat(stored.consumedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(stored.consumedByOperationId()).isEqualTo(operation.id());
        assertThat(approvals.findOpenByInstitutionId(master.institutionId(), NOW)).isEmpty();
    }

    @Test
    void consumeRejectsSelfApprovalExpiryOtherGenerationVersionDriftAndParameterDrift() {
        InstitutionSignKey master = insertActiveMaster();
        InstitutionSignKey otherGeneration = insertActiveMaster();
        UUID approver = insertUser();
        UUID requester = insertUser();
        String key = "ROTATE-" + UUID.randomUUID();
        KmsApproval approval = KmsApproval.create(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            hash(KmsAction.ROTATE, key), approver, NOW);
        approvals.insert(approval);
        KmsOperation operation = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.ROTATE, null, null,
            requester, approval.id(), key, NOW);
        operations.insert(operation);
        KmsApprovalConsumption valid = consumption(approval, master, requester, key, operation.id());

        assertThat(approvals.consume(with(valid, approver, valid.signKeyId(), valid.currentVersion(),
            valid.paramsHash(), valid.now()))).as("자기 승인").isFalse();
        assertThat(approvals.consume(with(valid, requester, valid.signKeyId(), valid.currentVersion(),
            valid.paramsHash(), NOW.plus(Duration.ofHours(25))))).as("만료").isFalse();
        assertThat(approvals.consume(with(valid, requester, otherGeneration.id(), valid.currentVersion(),
            valid.paramsHash(), valid.now()))).as("다른 세대").isFalse();
        assertThat(approvals.consume(with(valid, requester, valid.signKeyId(), valid.currentVersion() + 1,
            valid.paramsHash(), valid.now()))).as("버전 변동").isFalse();
        assertThat(approvals.consume(with(valid, requester, valid.signKeyId(), valid.currentVersion(),
            hash(KmsAction.ROTATE, "OTHER"), valid.now()))).as("파라미터 변경").isFalse();
        assertThat(approvals.consume(new KmsApprovalConsumption(valid.approvalId(), valid.institutionId(),
            valid.purpose(), KmsAction.RESUME, requester, valid.signKeyId(), valid.currentVersion(), null, null,
            hash(KmsAction.RESUME, key), operation.id(), NOW))).as("다른 행위").isFalse();

        assertThat(approvals.consume(valid)).isTrue();
    }

    @Test
    void compromiseApprovalBindsTargetVersionAndCompromisedAt() {
        InstitutionSignKey master = insertActiveMaster();
        UUID approver = insertUser();
        UUID requester = insertUser();
        String key = "COMPROMISE-" + UUID.randomUUID();
        Instant compromisedAt = NOW.minus(Duration.ofHours(2));
        KmsApproval approval = KmsApproval.create(UUID.randomUUID(), master, KmsAction.COMPROMISE, 1,
            compromisedAt, hash(KmsAction.COMPROMISE, key), approver, NOW);
        approvals.insert(approval);
        KmsOperation operation = KmsOperation.prepared(UUID.randomUUID(), master, KmsAction.COMPROMISE, 1, null,
            requester, approval.id(), key, NOW);
        operations.insert(operation);

        assertThat(approvals.consume(new KmsApprovalConsumption(approval.id(), master.institutionId(),
            master.purpose(), KmsAction.COMPROMISE, requester, master.id(), master.currentVersion(), 2,
            compromisedAt, hash(KmsAction.COMPROMISE, key), operation.id(), NOW.plusSeconds(1))))
            .as("다른 버전").isFalse();
        assertThat(approvals.consume(new KmsApprovalConsumption(approval.id(), master.institutionId(),
            master.purpose(), KmsAction.COMPROMISE, requester, master.id(), master.currentVersion(), 1,
            compromisedAt.minusSeconds(60), hash(KmsAction.COMPROMISE, key), operation.id(), NOW.plusSeconds(1))))
            .as("과거로 당긴 사고 시각").isFalse();
        assertThat(approvals.consume(new KmsApprovalConsumption(approval.id(), master.institutionId(),
            master.purpose(), KmsAction.COMPROMISE, requester, master.id(), master.currentVersion(), 1,
            compromisedAt, hash(KmsAction.COMPROMISE, key), operation.id(), NOW.plusSeconds(1))))
            .isTrue();
    }

    static KmsApprovalConsumption consumption(KmsApproval approval, InstitutionSignKey master, UUID requester,
        String idempotencyKey, UUID operationId) {
        return new KmsApprovalConsumption(approval.id(), master.institutionId(), master.purpose(),
            approval.action(), requester, master.id(), master.currentVersion(), null, null,
            hash(approval.action(), idempotencyKey), operationId, NOW.plusSeconds(1));
    }

    static KmsApprovalConsumption with(KmsApprovalConsumption base, UUID requester, UUID signKeyId,
        int currentVersion, String paramsHash, Instant now) {
        return new KmsApprovalConsumption(base.approvalId(), base.institutionId(), base.purpose(), base.action(),
            requester, signKeyId, currentVersion, base.targetKeyVersion(), base.targetCompromisedAt(), paramsHash,
            base.operationId(), now);
    }

    static String hash(KmsAction action, String idempotencyKey) {
        return KmsApprovalParams.hash(action, SignKeyPurpose.CREDENTIAL, idempotencyKey);
    }

    InstitutionSignKey insertActiveMaster() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey key = InstitutionSignKey.provisioning(
            UUID.randomUUID(), institutionId, SignKeyPurpose.CREDENTIAL, 1, NOW).activate(2, NOW);
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
