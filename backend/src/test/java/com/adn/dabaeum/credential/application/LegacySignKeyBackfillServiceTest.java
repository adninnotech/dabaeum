package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialPublicJwkFactory;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.LegacySignKeyBackfillRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyStatus;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 설계서 §9.2 전환 이전 키 호환 행, §11.3 백필. 가짜 저장소로 흐름과 멱등성을 본다. */
class LegacySignKeyBackfillServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");

    private final FakeKeys keys = new FakeKeys();
    private final FakeVersions versions = new FakeVersions();
    private final FakeBackfill backfill = new FakeBackfill();
    private final CredentialPublicJwkFactory jwkFactory = new CredentialPublicJwkFactory() {
        @Override
        public Map<String, String> create(String kid, PublicKey key) {
            Map<String, String> jwk = new LinkedHashMap<>();
            jwk.put("kid", kid);
            jwk.put("kty", "OKP");
            jwk.put("crv", "Ed25519");
            jwk.put("alg", "EdDSA");
            jwk.put("use", "sig");
            jwk.put("x", "AA");
            return jwk;
        }

        @Override
        public PublicKey publicKey(String storedJwk) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, String> publish(String keyId, String storedJwk) {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    void 기관마다_PROVISIONING_마스터와_버전_0_행을_만들고_기존_행을_연결한다() throws Exception {
        UUID institutionId = UUID.randomUUID();
        backfill.institutions.add(institutionId);
        backfill.credentialsToLink.put(institutionId, 3);
        backfill.statusListsToLink.put(institutionId, 1);

        List<LegacySignKeyBackfillService.Result> results =
            service(publicKey()).backfill(NOW);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.institutionId()).isEqualTo(institutionId);
            assertThat(result.masterCreated()).isTrue();
            assertThat(result.versionCreated()).isTrue();
            assertThat(result.credentialsLinked()).isEqualTo(3);
            assertThat(result.statusListsLinked()).isEqualTo(1);
        });
        InstitutionSignKey master = keys.findCurrent(institutionId, SignKeyPurpose.CREDENTIAL).orElseThrow();
        assertThat(master.status()).isEqualTo(SignKeyStatus.PROVISIONING);
        assertThat(master.currentVersion()).isZero();
        InstitutionSignKeyVersion legacy = versions.findBySignKeyIdAndVersion(master.id(), 0).orElseThrow();
        assertThat(legacy.kidFragment()).isEqualTo("development-1");
        assertThat(legacy.status()).isEqualTo(SignKeyVersionStatus.RETIRED);
        assertThat(legacy.publicKeyJwk()).doesNotContain("\"kid\"").contains("\"x\":\"AA\"");
        assertThat(backfill.linkedWith).containsEntry(institutionId, master.id());
    }

    @Test
    void 두_번_실행해도_행을_다시_만들지_않는다() throws Exception {
        UUID institutionId = UUID.randomUUID();
        backfill.institutions.add(institutionId);
        backfill.credentialsToLink.put(institutionId, 2);
        LegacySignKeyBackfillService service = service(publicKey());
        service.backfill(NOW);
        backfill.credentialsToLink.put(institutionId, 0);

        List<LegacySignKeyBackfillService.Result> second = service.backfill(NOW.plusSeconds(1));

        assertThat(second).singleElement().satisfies(result -> {
            assertThat(result.masterCreated()).isFalse();
            assertThat(result.versionCreated()).isFalse();
            assertThat(result.credentialsLinked()).isZero();
        });
        assertThat(keys.rows).hasSize(1);
        assertThat(versions.rows).hasSize(1);
    }

    @Test
    void 이미_ACTIVE_인_마스터가_있으면_버전_0_행만_더한다() throws Exception {
        UUID institutionId = UUID.randomUUID();
        InstitutionSignKey active = InstitutionSignKey.provisioning(
            UUID.randomUUID(), institutionId, SignKeyPurpose.CREDENTIAL, 1, NOW).activate(1, NOW);
        keys.insert(active);
        backfill.institutions.add(institutionId);

        service(publicKey()).backfill(NOW);

        assertThat(keys.rows).hasSize(1);
        assertThat(versions.findBySignKeyIdAndVersion(active.id(), 0)).isPresent();
        assertThat(backfill.linkedWith).containsEntry(institutionId, active.id());
    }

    @Test
    void 플랫폼_공개키가_없으면_아무_것도_바꾸지_않고_거부한다() {
        backfill.institutions.add(UUID.randomUUID());

        assertThatIllegalStateException().isThrownBy(() -> service(null).backfill(NOW));
        assertThat(keys.rows).isEmpty();
    }

    @Test
    void 설정된_키_버전이_레거시_kid_조각과_다르면_아무_것도_바꾸지_않고_거부한다() throws Exception {
        backfill.institutions.add(UUID.randomUUID());

        assertThatIllegalStateException()
            .isThrownBy(() -> service(publicKey(), "other-key-1").backfill(NOW));
        assertThat(keys.rows).isEmpty();
        assertThat(versions.rows).isEmpty();
    }

    @Test
    void 세대가_바뀌어도_버전_0_행은_1세대_마스터에_붙는다() throws Exception {
        UUID institutionId = UUID.randomUUID();
        InstitutionSignKey compromisedGeneration1 = InstitutionSignKey.provisioning(
                UUID.randomUUID(), institutionId, SignKeyPurpose.CREDENTIAL, 1, NOW)
            .activate(1, NOW)
            .compromise("openbao breach", NOW);
        InstitutionSignKey currentGeneration2 = InstitutionSignKey.provisioning(
                UUID.randomUUID(), institutionId, SignKeyPurpose.CREDENTIAL, 2, NOW)
            .activate(1, NOW);
        keys.insert(compromisedGeneration1);
        keys.insert(currentGeneration2);
        backfill.institutions.add(institutionId);

        List<LegacySignKeyBackfillService.Result> results = service(publicKey()).backfill(NOW);

        assertThat(results).singleElement().satisfies(result ->
            assertThat(result.masterCreated()).isFalse());
        assertThat(keys.rows).hasSize(2);
        assertThat(versions.findBySignKeyIdAndVersion(compromisedGeneration1.id(), 0)).isPresent();
        assertThat(versions.findBySignKeyIdAndVersion(currentGeneration2.id(), 0)).isEmpty();
        assertThat(backfill.linkedWith).containsEntry(institutionId, compromisedGeneration1.id());
    }

    @Test
    void 대상_기관이_없으면_빈_결과다() throws Exception {
        assertThat(service(publicKey()).backfill(NOW)).isEmpty();
    }

    private LegacySignKeyBackfillService service(PublicKey publicKey) {
        return service(publicKey, "development-1");
    }

    private LegacySignKeyBackfillService service(PublicKey publicKey, String keyVersion) {
        VcProperties properties = new VcProperties("http://localhost:8080", "v1", keyVersion);
        return new LegacySignKeyBackfillService(keys, versions, backfill, jwkFactory, properties, () -> publicKey);
    }

    private static PublicKey publicKey() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
    }

    static final class FakeKeys implements InstitutionSignKeyRepository {
        final List<InstitutionSignKey> rows = new ArrayList<>();

        @Override public void insert(InstitutionSignKey key) { rows.add(key); }

        @Override public Optional<InstitutionSignKey> findById(UUID id) {
            return rows.stream().filter(k -> k.id().equals(id)).findFirst();
        }

        @Override public Optional<InstitutionSignKey> findCurrent(UUID institutionId, SignKeyPurpose purpose) {
            return rows.stream().filter(k -> k.institutionId().equals(institutionId)
                && k.purpose() == purpose && k.isCurrent()).findFirst();
        }

        @Override public List<InstitutionSignKey> findByInstitutionId(UUID institutionId) {
            return rows.stream().filter(k -> k.institutionId().equals(institutionId)).toList();
        }

        @Override public boolean update(InstitutionSignKey key, int expectedVersionNo) {
            throw new UnsupportedOperationException();
        }
    }

    static final class FakeVersions implements InstitutionSignKeyVersionRepository {
        final List<InstitutionSignKeyVersion> rows = new ArrayList<>();

        @Override public void insert(InstitutionSignKeyVersion version) { rows.add(version); }

        @Override public Optional<InstitutionSignKeyVersion> findById(UUID id) {
            return rows.stream().filter(v -> v.id().equals(id)).findFirst();
        }

        @Override public Optional<InstitutionSignKeyVersion> findBySignKeyIdAndVersion(UUID signKeyId, int keyVersion) {
            return rows.stream().filter(v -> v.signKeyId().equals(signKeyId) && v.keyVersion() == keyVersion).findFirst();
        }

        @Override public Optional<InstitutionSignKeyVersion> findBySignKeyIdAndVersionForUpdate(UUID signKeyId,
            int keyVersion) {
            return findBySignKeyIdAndVersion(signKeyId, keyVersion);
        }

        @Override public Optional<InstitutionSignKeyVersion> findByInstitutionIdAndKidFragment(UUID institutionId, String kidFragment) {
            return rows.stream().filter(v -> v.institutionId().equals(institutionId) && v.kidFragment().equals(kidFragment)).findFirst();
        }

        @Override public List<InstitutionSignKeyVersion> findBySignKeyId(UUID signKeyId) {
            return rows.stream().filter(v -> v.signKeyId().equals(signKeyId)).toList();
        }

        @Override public List<InstitutionSignKeyVersion> findByInstitutionId(UUID institutionId) {
            return rows.stream().filter(v -> v.institutionId().equals(institutionId)).toList();
        }

        @Override public boolean retireIfActive(UUID id, Instant retiredAt) { throw new UnsupportedOperationException(); }

        @Override public boolean compromise(UUID id, Instant approvedCompromisedAt) { throw new UnsupportedOperationException(); }

        @Override public Optional<SignKeyVersionStatus> findStatusForShare(UUID signKeyId, int keyVersion) {
            throw new UnsupportedOperationException();
        }
    }

    static final class FakeBackfill implements LegacySignKeyBackfillRepository {
        final List<UUID> institutions = new ArrayList<>();
        final Map<UUID, Integer> credentialsToLink = new HashMap<>();
        final Map<UUID, Integer> statusListsToLink = new HashMap<>();
        final Map<UUID, UUID> linkedWith = new HashMap<>();

        @Override public List<UUID> findInstitutionsWithUnlinkedRows() { return List.copyOf(institutions); }

        @Override public int linkCredentials(UUID institutionId, UUID signKeyId) {
            linkedWith.put(institutionId, signKeyId);
            return credentialsToLink.getOrDefault(institutionId, 0);
        }

        @Override public int linkStatusLists(UUID institutionId, UUID signKeyId) {
            linkedWith.put(institutionId, signKeyId);
            return statusListsToLink.getOrDefault(institutionId, 0);
        }
    }
}
