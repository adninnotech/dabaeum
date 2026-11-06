package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialSigningException;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionListItem;
import com.adn.dabaeum.institution.domain.InstitutionPageCriteria;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 설계서 §8.1 2단계: 기관, 현재 세대 마스터, 현재 버전 행이 모두 ACTIVE 일 때만 서명 키를 고른다. */
class CredentialSigningKeyGateTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final UUID INSTITUTION = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String JWK =
        "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\"AA\"}";
    private static final String ISSUER =
        "https://vc.example.test/api/v1/vc/issuers/20000000-0000-0000-0000-000000000002";

    private final FakeInstitutions institutions = new FakeInstitutions();
    private final FakeKeys keys = new FakeKeys();
    private final FakeVersions versions = new FakeVersions();
    private final CredentialSigningKeyGate gate = new CredentialSigningKeyGate(institutions, keys, versions,
        new DefaultCredentialUriProvider(new VcProperties("https://vc.example.test", "v1", "development-1")));

    @Test
    void 활성_기관의_현재_세대_ACTIVE_버전과_kid_를_고른다() {
        institutions.put(InstitutionStatus.ACTIVE, null);
        InstitutionSignKey master = activeKey(1, 2);
        InstitutionSignKeyVersion v2 = version(master, 2);

        SigningKeySelection selection = gate.select(INSTITUTION);

        assertThat(selection.master()).isEqualTo(master);
        assertThat(selection.version()).isEqualTo(v2);
        assertThat(selection.keyId()).isEqualTo(ISSUER + "#v2");
    }

    @Test
    void 기관이_없거나_ACTIVE_가_아니거나_삭제됐으면_거부한다() {
        activeKey(1, 1);
        assertCode(CredentialSigningException.INSTITUTION_INACTIVE);

        institutions.put(InstitutionStatus.SUSPENDED, null);
        assertCode(CredentialSigningException.INSTITUTION_INACTIVE);

        institutions.put(InstitutionStatus.ACTIVE, NOW);
        assertCode(CredentialSigningException.INSTITUTION_INACTIVE);
    }

    @Test
    void 마스터가_없거나_백필로_만든_PROVISIONING_이면_거부한다() {
        institutions.put(InstitutionStatus.ACTIVE, null);
        assertCode(CredentialSigningException.KEY_INACTIVE);

        keys.rows.add(InstitutionSignKey.provisioning(
            UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, NOW));
        assertCode(CredentialSigningException.KEY_INACTIVE);
    }

    @Test
    void 마스터가_ACTIVE_여도_현재_버전_행이_COMPROMISED_면_거부한다() {
        institutions.put(InstitutionStatus.ACTIVE, null);
        InstitutionSignKey master = activeKey(1, 1);
        versions.rows.add(version(master, 1).compromise(NOW));
        versions.rows.removeIf(row -> row.status() == SignKeyVersionStatus.ACTIVE);

        assertCode(CredentialSigningException.KEY_COMPROMISED);
    }

    @Test
    void 세대가_바뀌면_현재_세대의_kid_조각을_쓴다() {
        institutions.put(InstitutionStatus.ACTIVE, null);
        InstitutionSignKey first = InstitutionSignKey.provisioning(
            UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, NOW)
            .activate(1, NOW).compromise("openbao breach", NOW);
        keys.rows.add(first);
        InstitutionSignKey second = activeKey(2, 1);
        version(second, 1);

        assertThat(gate.select(INSTITUTION).keyId()).isEqualTo(ISSUER + "#g2-v1");
    }

    private void assertCode(String code) {
        assertThatThrownBy(() -> gate.select(INSTITUTION))
            .isInstanceOfSatisfying(CredentialSigningException.class, exception -> {
                assertThat(exception.code()).isEqualTo(code);
                assertThat(exception.disposition()).isEqualTo(CredentialSigningException.Disposition.FAIL);
            });
    }

    private InstitutionSignKey activeKey(int generation, int currentVersion) {
        InstitutionSignKey key = InstitutionSignKey.provisioning(
            UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, generation, NOW).activate(1, NOW);
        for (int version = 2; version <= currentVersion; version++) {
            key = key.rotated(version, NOW);
        }
        keys.rows.add(key);
        return key;
    }

    private InstitutionSignKeyVersion version(InstitutionSignKey master, int keyVersion) {
        InstitutionSignKeyVersion version = InstitutionSignKeyVersion.active(UUID.randomUUID(), master.id(),
            INSTITUTION, SignKeyPurpose.CREDENTIAL, master.generation(), keyVersion, JWK, NOW, null, NOW);
        versions.rows.add(version);
        return version;
    }

    static final class FakeInstitutions implements InstitutionRepository {
        @Override public long countActive(String keyword, InstitutionStatus status, String region) { throw new UnsupportedOperationException(); }
        Institution institution;

        void put(InstitutionStatus status, Instant deletedAt) {
            institution = new Institution(INSTITUTION, "INST-1", "기관", null, null, null, null, null,
                status, NOW, NOW, deletedAt);
        }

        @Override public void save(Institution value) { institution = value; }
        @Override public Optional<Institution> findById(UUID id) {
            return Optional.ofNullable(institution).filter(value -> value.id().equals(id));
        }
        @Override public List<Institution> findByIds(java.util.Collection<UUID> ids) { throw new UnsupportedOperationException(); }
        @Override public Optional<Institution> findActiveById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public Optional<Institution> findActiveByCode(String code) { throw new UnsupportedOperationException(); }
        @Override public List<InstitutionListItem> findActivePage(InstitutionPageCriteria criteria) { throw new UnsupportedOperationException(); }
        @Override public boolean updateActive(Institution value) { throw new UnsupportedOperationException(); }
    }

    static final class FakeKeys implements InstitutionSignKeyRepository {
        final List<InstitutionSignKey> rows = new ArrayList<>();

        @Override public void insert(InstitutionSignKey key) { rows.add(key); }
        @Override public Optional<InstitutionSignKey> findById(UUID id) {
            return rows.stream().filter(key -> key.id().equals(id)).findFirst();
        }
        @Override public Optional<InstitutionSignKey> findCurrent(UUID institutionId, SignKeyPurpose purpose) {
            return rows.stream().filter(key -> key.institutionId().equals(institutionId)
                && key.purpose() == purpose && key.isCurrent()).findFirst();
        }
        @Override public List<InstitutionSignKey> findByInstitutionId(UUID institutionId) {
            return rows.stream().filter(key -> key.institutionId().equals(institutionId)).toList();
        }
        @Override public boolean update(InstitutionSignKey key, int expectedVersionNo) {
            throw new UnsupportedOperationException();
        }
    }

    static final class FakeVersions implements InstitutionSignKeyVersionRepository {
        final List<InstitutionSignKeyVersion> rows = new ArrayList<>();

        @Override public void insert(InstitutionSignKeyVersion version) { rows.add(version); }
        @Override public Optional<InstitutionSignKeyVersion> findById(UUID id) {
            return rows.stream().filter(row -> row.id().equals(id)).findFirst();
        }
        @Override public Optional<InstitutionSignKeyVersion> findBySignKeyIdAndVersion(UUID signKeyId, int keyVersion) {
            return rows.stream().filter(row -> row.signKeyId().equals(signKeyId)
                && row.keyVersion() == keyVersion).findFirst();
        }
        @Override public Optional<InstitutionSignKeyVersion> findBySignKeyIdAndVersionForUpdate(UUID signKeyId,
            int keyVersion) {
            return findBySignKeyIdAndVersion(signKeyId, keyVersion);
        }
        @Override public Optional<InstitutionSignKeyVersion> findByInstitutionIdAndKidFragment(UUID id, String fragment) {
            throw new UnsupportedOperationException();
        }
        @Override public List<InstitutionSignKeyVersion> findBySignKeyId(UUID signKeyId) { throw new UnsupportedOperationException(); }
        @Override public List<InstitutionSignKeyVersion> findByInstitutionId(UUID id) { throw new UnsupportedOperationException(); }
        @Override public boolean retireIfActive(UUID id, Instant retiredAt) { throw new UnsupportedOperationException(); }
        @Override public boolean compromise(UUID id, Instant at) { throw new UnsupportedOperationException(); }
        @Override public Optional<SignKeyVersionStatus> findStatusForShare(UUID signKeyId, int keyVersion) {
            return findBySignKeyIdAndVersion(signKeyId, keyVersion).map(InstitutionSignKeyVersion::status);
        }
    }
}
