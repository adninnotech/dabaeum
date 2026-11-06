package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.domain.CredentialVerificationKeyResolver.DocumentKind;
import com.adn.dabaeum.credential.domain.CredentialVerificationKeyResolver.KeyResolutionException;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.credential.domain.VerificationKey;
import com.adn.dabaeum.credential.infrastructure.crypto.Ed25519PublicJwkFactory;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** 설계서 §9 3단계: kid 조각 → 기관 버전 행 → 목적 대조 → 보존 공개키. */
class DefaultCredentialVerificationKeyResolverTest {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private static final UUID INSTITUTION = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_INSTITUTION = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final Ed25519PublicJwkFactory JWKS = new Ed25519PublicJwkFactory();

    private final FakeKeys keys = new FakeKeys();
    private final FakeVersions versions = new FakeVersions();
    private PublicKey fileKey;
    private final DefaultCredentialVerificationKeyResolver resolver =
        new DefaultCredentialVerificationKeyResolver(versions, keys, JWKS, () -> fileKey);

    @Test
    void 버전_행의_보존_공개키와_상태를_돌려준다() throws Exception {
        InstitutionSignKey master = master(INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, 2);
        PublicKey v2 = newKey();
        versions.rows.add(row(master, 2, v2));

        VerificationKey key = resolver.resolve(INSTITUTION, "v2", DocumentKind.CREDENTIAL);

        assertThat(key.publicKey().getEncoded()).isEqualTo(v2.getEncoded());
        assertThat(key.kidFragment()).isEqualTo("v2");
        assertThat(key.signKeyId()).isEqualTo(master.id());
        assertThat(key.keyVersion()).isEqualTo(2);
        assertThat(key.status()).isEqualTo(SignKeyVersionStatus.ACTIVE);
        assertThat(key.compromised()).isFalse();
    }

    @Test
    void 퇴역_버전과_유출_버전도_보존_공개키로_돌려준다() throws Exception {
        InstitutionSignKey master = master(INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, 3);
        versions.rows.add(row(master, 1, newKey()).retire(NOW));
        versions.rows.add(row(master, 2, newKey()).compromise(NOW.minusSeconds(60)));

        assertThat(resolver.resolve(INSTITUTION, "v1", DocumentKind.CREDENTIAL).status())
            .isEqualTo(SignKeyVersionStatus.RETIRED);
        VerificationKey compromised = resolver.resolve(INSTITUTION, "v2", DocumentKind.CREDENTIAL);
        assertThat(compromised.compromised()).isTrue();
        assertThat(compromised.compromisedAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void 다른_기관의_조각이거나_없는_조각이면_기관_불일치다() throws Exception {
        InstitutionSignKey other = master(OTHER_INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, 1);
        versions.rows.add(row(other, 1, newKey()));

        assertCode(() -> resolver.resolve(INSTITUTION, "v1", DocumentKind.CREDENTIAL),
            KeyResolutionException.ISSUER_MISMATCH);
        assertCode(() -> resolver.resolve(INSTITUTION, "v9", DocumentKind.CREDENTIAL),
            KeyResolutionException.ISSUER_MISMATCH);
        assertCode(() -> resolver.resolve(INSTITUTION, " ", DocumentKind.CREDENTIAL),
            KeyResolutionException.ISSUER_MISMATCH);
    }

    @Test
    void 키_목적이_문서_종류와_다르면_거부한다() throws Exception {
        InstitutionSignKey credential = master(INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, 1);
        InstitutionSignKey statusList = master(INSTITUTION, SignKeyPurpose.STATUS_LIST, 1, 1);
        versions.rows.add(row(credential, 1, newKey()));
        versions.rows.add(row(statusList, 1, newKey()));

        assertCode(() -> resolver.resolve(INSTITUTION, "v1", DocumentKind.STATUS_LIST),
            KeyResolutionException.PURPOSE_MISMATCH);
        assertCode(() -> resolver.resolve(INSTITUTION, "status-v1", DocumentKind.CREDENTIAL),
            KeyResolutionException.PURPOSE_MISMATCH);
        assertThat(resolver.resolve(INSTITUTION, "status-v1", DocumentKind.STATUS_LIST).signKeyId())
            .isEqualTo(statusList.id());
    }

    @Test
    void 전환_이전_키는_버전_0_행이_있으면_그_키를_두_종류에_쓰고_없으면_파일_키로_대신한다() throws Exception {
        fileKey = newKey();
        VerificationKey fromFile = resolver.resolve(INSTITUTION, "development-1", DocumentKind.STATUS_LIST);
        assertThat(fromFile.publicKey()).isSameAs(fileKey);
        assertThat(fromFile.signKeyId()).isNull();
        assertThat(fromFile.keyVersion()).isNull();

        InstitutionSignKey master = master(INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, 0);
        PublicKey legacy = newKey();
        versions.rows.add(InstitutionSignKeyVersion.legacy(
            UUID.randomUUID(), master.id(), INSTITUTION, storedJwk(legacy), NOW));

        for (DocumentKind kind : DocumentKind.values()) {
            VerificationKey fromRow = resolver.resolve(INSTITUTION, "development-1", kind);
            assertThat(fromRow.publicKey().getEncoded()).isEqualTo(legacy.getEncoded());
            assertThat(fromRow.keyVersion()).isZero();
            assertThat(fromRow.signKeyId()).isEqualTo(master.id());
        }
    }

    @Test
    void 파일_키도_버전_행도_없거나_저장된_키를_읽을_수_없으면_사용_불가다() throws Exception {
        assertCode(() -> resolver.resolve(INSTITUTION, "development-1", DocumentKind.CREDENTIAL),
            KeyResolutionException.UNAVAILABLE);

        InstitutionSignKey master = master(INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, 1);
        versions.rows.add(new InstitutionSignKeyVersion(UUID.randomUUID(), master.id(), INSTITUTION, 1,
            "v1", "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\"AA\"}",
            InstitutionSignKeyVersion.Source.OPENBAO, SignKeyVersionStatus.ACTIVE,
            NOW, NOW, null, null, null, NOW));
        assertCode(() -> resolver.resolve(INSTITUTION, "v1", DocumentKind.CREDENTIAL),
            KeyResolutionException.UNAVAILABLE);

        keys.rows.clear();
        assertCode(() -> resolver.resolve(INSTITUTION, "v1", DocumentKind.CREDENTIAL),
            KeyResolutionException.UNAVAILABLE);

        versions.failure = true;
        assertCode(() -> resolver.resolve(INSTITUTION, "v1", DocumentKind.CREDENTIAL),
            KeyResolutionException.UNAVAILABLE);
    }

    private static void assertCode(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(KeyResolutionException.class,
            exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private InstitutionSignKey master(
        UUID institutionId, SignKeyPurpose purpose, int generation, int currentVersion
    ) {
        InstitutionSignKey key = InstitutionSignKey.provisioning(
            UUID.randomUUID(), institutionId, purpose, generation, NOW);
        if (currentVersion >= 1) {
            key = key.activate(1, NOW);
            for (int version = 2; version <= currentVersion; version++) {
                key = key.rotated(version, NOW);
            }
        }
        keys.rows.add(key);
        return key;
    }

    private static InstitutionSignKeyVersion row(InstitutionSignKey master, int keyVersion, PublicKey key) {
        return InstitutionSignKeyVersion.active(UUID.randomUUID(), master.id(), master.institutionId(),
            master.purpose(), master.generation(), keyVersion, storedJwk(key), NOW, null, NOW);
    }

    static String storedJwk(PublicKey publicKey) {
        Map<String, String> jwk = new LinkedHashMap<>(JWKS.create("ignored", publicKey));
        return "{\"kty\":\"" + jwk.get("kty") + "\",\"crv\":\"" + jwk.get("crv")
            + "\",\"alg\":\"" + jwk.get("alg") + "\",\"use\":\"" + jwk.get("use")
            + "\",\"x\":\"" + jwk.get("x") + "\"}";
    }

    private static PublicKey newKey() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
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
        boolean failure;

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
        @Override public Optional<InstitutionSignKeyVersion> findByInstitutionIdAndKidFragment(
            UUID institutionId, String kidFragment
        ) {
            if (failure) {
                throw new IllegalStateException("database unavailable");
            }
            return rows.stream().filter(row -> row.institutionId().equals(institutionId)
                && row.kidFragment().equals(kidFragment)).findFirst();
        }
        @Override public List<InstitutionSignKeyVersion> findBySignKeyId(UUID signKeyId) {
            throw new UnsupportedOperationException();
        }
        @Override public List<InstitutionSignKeyVersion> findByInstitutionId(UUID institutionId) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean retireIfActive(UUID id, Instant retiredAt) { throw new UnsupportedOperationException(); }
        @Override public boolean compromise(UUID id, Instant at) { throw new UnsupportedOperationException(); }
        @Override public Optional<SignKeyVersionStatus> findStatusForShare(UUID signKeyId, int keyVersion) {
            throw new UnsupportedOperationException();
        }
    }
}
