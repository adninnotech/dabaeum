package com.adn.dabaeum.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyStatus;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/** 기관 서명키 마스터·버전 매퍼 SQL 을 실제 PostgreSQL 로 확인한다 (설계서 §11.1, §11.2). */
class InstitutionSignKeyPersistenceIntegrationTest extends RemotePostgresIntegrationTestSupport {

    static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");

    static final String JWK =
        "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\"AA\"}";

    @Autowired
    InstitutionSignKeyRepository keys;

    @Autowired
    InstitutionSignKeyVersionRepository versions;

    @Test
    void roundTripsAMasterAndFindsTheCurrentOnePerPurpose() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey credential = InstitutionSignKey.provisioning(
            UUID.randomUUID(), institutionId, SignKeyPurpose.CREDENTIAL, 1, NOW);
        InstitutionSignKey statusList = InstitutionSignKey.provisioning(
            UUID.randomUUID(), institutionId, SignKeyPurpose.STATUS_LIST, 1, NOW);
        keys.insert(credential);
        keys.insert(statusList);

        assertThat(keys.findById(credential.id())).contains(credential);
        assertThat(keys.findCurrent(institutionId, SignKeyPurpose.CREDENTIAL)).contains(credential);
        assertThat(keys.findCurrent(institutionId, SignKeyPurpose.STATUS_LIST)).contains(statusList);
        assertThat(keys.findCurrent(UUID.randomUUID(), SignKeyPurpose.CREDENTIAL)).isEmpty();
        assertThat(keys.findByInstitutionId(institutionId))
            .extracting(InstitutionSignKey::purpose)
            .containsExactly(SignKeyPurpose.CREDENTIAL, SignKeyPurpose.STATUS_LIST);
    }

    @Test
    void updateUsesVersionNoAsAnOptimisticLock() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey provisioning = InstitutionSignKey.provisioning(
            UUID.randomUUID(), institutionId, SignKeyPurpose.CREDENTIAL, 1, NOW);
        keys.insert(provisioning);

        InstitutionSignKey active = provisioning.activate(1, NOW.plusSeconds(1));
        assertThat(keys.update(active, provisioning.versionNo())).isTrue();
        assertThat(keys.findById(active.id())).contains(active);

        // 같은 원본에서 만든 두 번째 전이는 이미 지나간 version_no 를 기대하므로 0행이다.
        InstitutionSignKey stale = provisioning.markError("late", NOW.plusSeconds(2));
        assertThat(keys.update(stale, provisioning.versionNo())).isFalse();
        assertThat(keys.findById(active.id()).orElseThrow().status()).isEqualTo(SignKeyStatus.ACTIVE);
    }

    @Test
    void compromisingTheCurrentGenerationLetsANewGenerationBecomeCurrent() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey first = InstitutionSignKey.provisioning(
            UUID.randomUUID(), institutionId, SignKeyPurpose.CREDENTIAL, 1, NOW).activate(1, NOW);
        keys.insert(first);

        InstitutionSignKey compromised = first.compromise("openbao breach", NOW.plusSeconds(1));
        assertThat(keys.update(compromised, first.versionNo())).isTrue();

        InstitutionSignKey second = InstitutionSignKey.provisioning(
            UUID.randomUUID(), institutionId, SignKeyPurpose.CREDENTIAL, 2, NOW.plusSeconds(2));
        keys.insert(second);

        assertThat(keys.findCurrent(institutionId, SignKeyPurpose.CREDENTIAL)).contains(second);
        assertThat(keys.findByInstitutionId(institutionId))
            .extracting(InstitutionSignKey::generation).containsExactly(1, 2);
    }

    @Test
    void roundTripsVersionsAndFindsThemByKidFragment() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey key = insertActiveKey(institutionId, SignKeyPurpose.CREDENTIAL, 1);
        InstitutionSignKeyVersion v1 = InstitutionSignKeyVersion.active(UUID.randomUUID(), key.id(),
            institutionId, SignKeyPurpose.CREDENTIAL, 1, 1, JWK, NOW.minusSeconds(3), null, NOW);
        InstitutionSignKeyVersion v2 = InstitutionSignKeyVersion.active(UUID.randomUUID(), key.id(),
            institutionId, SignKeyPurpose.CREDENTIAL, 1, 2, JWK, NOW.minusSeconds(2), null, NOW);
        versions.insert(v1);
        versions.insert(v2);

        assertSameVersion(versions.findById(v1.id()), v1);
        assertSameVersion(versions.findBySignKeyIdAndVersion(key.id(), 2), v2);
        assertSameVersion(versions.findByInstitutionIdAndKidFragment(institutionId, "v2"), v2);
        assertThat(versions.findByInstitutionIdAndKidFragment(institutionId, "v9")).isEmpty();
        assertThat(versions.findBySignKeyId(key.id()))
            .extracting(InstitutionSignKeyVersion::keyVersion).containsExactly(1, 2);
    }

    @Test
    void findByInstitutionIdOrdersByGenerationThenVersionAcrossPurposes() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey credential = insertActiveKey(institutionId, SignKeyPurpose.CREDENTIAL, 1);
        InstitutionSignKey status = insertActiveKey(institutionId, SignKeyPurpose.STATUS_LIST, 1);
        versions.insert(InstitutionSignKeyVersion.active(UUID.randomUUID(), status.id(),
            institutionId, SignKeyPurpose.STATUS_LIST, 1, 1, JWK, null, null, NOW));
        versions.insert(InstitutionSignKeyVersion.active(UUID.randomUUID(), credential.id(),
            institutionId, SignKeyPurpose.CREDENTIAL, 1, 2, JWK, null, null, NOW));
        versions.insert(InstitutionSignKeyVersion.active(UUID.randomUUID(), credential.id(),
            institutionId, SignKeyPurpose.CREDENTIAL, 1, 1, JWK, null, null, NOW));
        versions.insert(InstitutionSignKeyVersion.legacy(UUID.randomUUID(), credential.id(),
            institutionId, JWK, NOW));

        assertThat(versions.findByInstitutionId(institutionId))
            .extracting(InstitutionSignKeyVersion::kidFragment)
            .containsExactly("development-1", "v1", "v2", "status-v1");
    }

    @Test
    void retireIfActiveTouchesOnlyActiveRows() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey key = insertActiveKey(institutionId, SignKeyPurpose.CREDENTIAL, 1);
        InstitutionSignKeyVersion v1 = InstitutionSignKeyVersion.active(UUID.randomUUID(), key.id(),
            institutionId, SignKeyPurpose.CREDENTIAL, 1, 1, JWK, null, null, NOW);
        versions.insert(v1);

        assertThat(versions.retireIfActive(v1.id(), NOW.plusSeconds(1))).isTrue();
        assertThat(versions.findById(v1.id()).orElseThrow().status())
            .isEqualTo(SignKeyVersionStatus.RETIRED);
        assertThat(versions.retireIfActive(v1.id(), NOW.plusSeconds(2))).isFalse();
    }

    @Test
    void compromiseIsIdempotentAndNeverUndoneByRetire() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey key = insertActiveKey(institutionId, SignKeyPurpose.CREDENTIAL, 1);
        InstitutionSignKeyVersion v1 = InstitutionSignKeyVersion.active(UUID.randomUUID(), key.id(),
            institutionId, SignKeyPurpose.CREDENTIAL, 1, 1, JWK, null, null, NOW);
        versions.insert(v1);

        assertThat(versions.compromise(v1.id(), NOW.plusSeconds(1))).isTrue();
        assertThat(versions.compromise(v1.id(), NOW.plusSeconds(2))).isFalse();
        // §6.2 T2 의 조건부 RETIRED 전이가 유출 버전을 덮어쓰지 못한다.
        assertThat(versions.retireIfActive(v1.id(), NOW.plusSeconds(3))).isFalse();

        InstitutionSignKeyVersion stored = versions.findById(v1.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(SignKeyVersionStatus.COMPROMISED);
        assertThat(stored.compromisedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void 버전_행을_FOR_UPDATE_로_읽으면_같은_행이_돌아오고_없는_버전은_비어_있다() {
        UUID institutionId = insertInstitution();
        InstitutionSignKey key = insertActiveKey(institutionId, SignKeyPurpose.CREDENTIAL, 1);
        InstitutionSignKeyVersion v1 = InstitutionSignKeyVersion.active(UUID.randomUUID(), key.id(),
            institutionId, SignKeyPurpose.CREDENTIAL, 1, 1, JWK, null, null, NOW);
        versions.insert(v1);

        assertSameVersion(versions.findBySignKeyIdAndVersionForUpdate(key.id(), 1), v1);
        assertThat(versions.findBySignKeyIdAndVersionForUpdate(key.id(), 99)).isEmpty();
    }

    private InstitutionSignKey insertActiveKey(UUID institutionId, SignKeyPurpose purpose, int generation) {
        InstitutionSignKey key = InstitutionSignKey.provisioning(
            UUID.randomUUID(), institutionId, purpose, generation, NOW).activate(1, NOW);
        keys.insert(key);
        return key;
    }

    UUID insertInstitution() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("institution"), uniqueCode("institution-name"));
        return institutionId;
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * public_key_jwk 는 jsonb 라 키 순서·공백이 정규화돼 돌아온다. 저장소 계약은 JSON 동치이지
     * 텍스트 동일성이 아니므로 JWK 만 파싱해 비교하고 나머지 컴포넌트는 그대로 비교한다.
     */
    private static void assertSameVersion(Optional<InstitutionSignKeyVersion> actual, InstitutionSignKeyVersion expected) {
        assertThat(actual).isPresent();
        InstitutionSignKeyVersion stored = actual.orElseThrow();
        assertThat(JSON.readTree(stored.publicKeyJwk())).isEqualTo(JSON.readTree(expected.publicKeyJwk()));
        assertThat(new InstitutionSignKeyVersion(stored.id(), stored.signKeyId(), stored.institutionId(),
            stored.keyVersion(), stored.kidFragment(), expected.publicKeyJwk(), stored.source(), stored.status(),
            stored.openbaoCreatedAt(), stored.activatedAt(), stored.retiredAt(), stored.compromisedAt(),
            stored.createdBy(), stored.createdAt())).isEqualTo(expected);
    }
}
