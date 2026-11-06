package com.adn.dabaeum.fabric.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.application.CredentialHashService;
import com.adn.dabaeum.credential.application.CredentialSigningKeyGate;
import com.adn.dabaeum.credential.application.SigningKeySelection;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningException;
import com.adn.dabaeum.credential.domain.CredentialSigningException.Disposition;
import com.adn.dabaeum.credential.domain.CredentialSigningInput;
import com.adn.dabaeum.credential.domain.CredentialSigningInputFactory;
import com.adn.dabaeum.credential.domain.CredentialSigningRepository;
import com.adn.dabaeum.credential.domain.CredentialSigningState;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import com.adn.dabaeum.credential.domain.TransitSignature;
import com.adn.dabaeum.credential.domain.TransitSigningException;
import com.adn.dabaeum.credential.domain.TransitSigningException.Reason;
import com.adn.dabaeum.credential.domain.TransitSigningPort;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 설계서 §7.3·§7.4·§8.1 2~8단계와 §20 의 경쟁·불일치·오래된 응답 행. */
class TransitCredentialIssuanceSignerTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final UUID CREDENTIAL = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID INSTITUTION = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String ISSUER = "https://vc.example.test/api/v1/vc/issuers/" + INSTITUTION;
    private static final String JWK =
        "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\"AA\"}";
    private static final String HASH = "e".repeat(64);
    private static final Function<SignedCredentialEnvelope, String> HASHER = envelope -> HASH;
    private static final byte[] SIGNATURE = filled(64, (byte) 1);
    private static final CredentialSigningInputFactory INPUTS = (document, institutionId, keyId) ->
        new CredentialSigningInput(b64("{\"kid\":\"" + keyId + "\"}"), b64(document.canonicalJson()));

    private final FakeSigning signing = new FakeSigning();
    private final InstitutionSignKeyRepository keys = mock(InstitutionSignKeyRepository.class);
    private final InstitutionSignKeyVersionRepository versions = mock(InstitutionSignKeyVersionRepository.class);
    private final CredentialSigningKeyGate gate = mock(CredentialSigningKeyGate.class);
    private final ScriptedTransit transit = new ScriptedTransit();
    private final TransitCredentialIssuanceSigner signer = new TransitCredentialIssuanceSigner(
        signing, signing.credentials(), keys, versions, gate, INPUTS, transit,
        new CredentialHashService(new ObjectMapper()));

    @Test
    void 첫_서명은_입력을_커밋하고_명시한_버전으로_서명한_뒤_봉투를_저장한다() {
        InstitutionSignKey master = master(2);
        select(master, version(master, 2));

        CredentialIssuanceSigner.Signed signed = signer.sign(CREDENTIAL, this::context, HASHER, NOW);

        assertThat(transit.requestedVersions).containsExactly(2);
        assertThat(transit.inputs.get(0)).isEqualTo((signing.header + "." + signing.payload)
            .getBytes(StandardCharsets.US_ASCII));
        assertThat(signing.attemptNo).isEqualTo(1);
        assertThat(signing.issuer).isEqualTo("urn:issuer");
        assertThat(signed.envelope().compactJws()).isEqualTo(signing.header + "." + signing.payload + "."
            + Base64.getUrlEncoder().withoutPadding().encodeToString(SIGNATURE));
        assertThat(signed.credential().vcPayload()).contains(signed.envelope().compactJws());
        assertThat(signed.credential().vcHash()).isEqualTo(HASH);
    }

    @Test
    void 사전_커밋된_입력은_문서를_다시_만들지_않고_같은_바이트를_서명한다() {
        InstitutionSignKey master = master(2);
        version(master, 2);
        signing.preCommit(master.id(), 2, new CredentialSigningInput("aGVhZGVy", "cGF5bG9hZA"));

        signer.sign(CREDENTIAL, () -> { throw new AssertionError("document must not be rebuilt"); }, HASHER, NOW);

        assertThat(transit.inputs.get(0)).isEqualTo("aGVhZGVy.cGF5bG9hZA".getBytes(StandardCharsets.US_ASCII));
        assertThat(signing.vcPayload).isNotNull();
        verifyNoInteractions(gate);
    }

    @Test
    void 응답_버전이_요청과_다르면_저장하지_않고_실패한다() {
        InstitutionSignKey master = master(2);
        select(master, version(master, 2));
        transit.script.add(requested -> new TransitSignature(requested + 1, SIGNATURE));

        assertFailure(CredentialSigningException.VERSION_MISMATCH, Disposition.FAIL);
        assertThat(signing.vcPayload).isNull();
    }

    @Test
    void 버전이_거부되면_같은_행에서_새_버전_헤더로_재커밋하고_다시_서명한다() {
        InstitutionSignKey v2Master = master(2);
        InstitutionSignKey v3Master = v2Master.rotated(3, NOW);
        SigningKeySelection v2 = selection(v2Master, version(v2Master, 2));
        SigningKeySelection v3 = selection(v3Master, version(v3Master, 3));
        when(gate.select(INSTITUTION)).thenReturn(v2, v3);
        transit.script.add(requested -> { throw new TransitSigningException(Reason.VERSION_REJECTED, "min", null); });

        signer.sign(CREDENTIAL, this::context, HASHER, NOW);

        assertThat(transit.requestedVersions).containsExactly(2, 3);
        assertThat(signing.attemptNo).isEqualTo(2);
        assertThat(signing.signKeyVersion).isEqualTo(3);
        assertThat(decode(signing.header)).endsWith("#v3\"}");
        assertThat(decode(signing.payload)).isEqualTo("{\"id\":\"doc\"}");
        assertThat(signing.vcPayload).isNotNull();
    }

    @Test
    void 버전이_거부됐는데_DB_현재_버전이_그대로면_실패한다() {
        InstitutionSignKey master = master(2);
        select(master, version(master, 2));
        transit.script.add(requested -> { throw new TransitSigningException(Reason.VERSION_REJECTED, "min", null); });

        assertFailure(CredentialSigningException.VERSION_REJECTED, Disposition.FAIL);
        assertThat(signing.recommits).isZero();
    }

    @Test
    void 서명_사이에_버전_행이_COMPROMISED_가_되면_저장하지_않고_실패한다() {
        InstitutionSignKey master = master(2);
        InstitutionSignKeyVersion active = version(master, 2);
        select(master, active);
        transit.duringSign = () -> when(versions.findBySignKeyIdAndVersion(master.id(), 2))
            .thenReturn(Optional.of(active.compromise(NOW)));

        assertFailure(CredentialSigningException.KEY_COMPROMISED, Disposition.FAIL);
        assertThat(signing.vcPayload).isNull();
    }

    @Test
    void 서명_중에_다른_시도가_행을_가져가면_오래된_응답으로_버린다() {
        InstitutionSignKey master = master(2);
        select(master, version(master, 2));
        transit.duringSign = () -> signing.attemptNo = 5;

        assertFailure(CredentialSigningException.STALE, Disposition.STALE);
        assertThat(signing.vcPayload).isNull();
    }

    @Test
    void OpenBao_일시_장애는_재시도로_두고_커밋한_입력은_남긴다() {
        InstitutionSignKey master = master(2);
        select(master, version(master, 2));
        transit.script.add(requested -> { throw new TransitSigningException(Reason.UNAVAILABLE, "sealed", null); });

        assertFailure(CredentialSigningException.UNAVAILABLE, Disposition.RETRY);
        assertThat(signing.header).isNotNull();
        assertThat(signing.attemptNo).isEqualTo(1);

        transit.script.add(requested -> { throw new TransitSigningException(Reason.REJECTED, "missing", null); });
        assertFailure(CredentialSigningException.REJECTED, Disposition.FAIL);
    }

    @Test
    void ISSUING_이_아니면_아무것도_부르지_않고_오래된_시도로_끝낸다() {
        signing.status = CredentialStatus.FAILED;

        assertFailure(CredentialSigningException.STALE, Disposition.STALE);
        assertThat(transit.requestedVersions).isEmpty();
        verifyNoInteractions(gate);
    }

    @Test
    void 게이트가_거부하면_문서를_만들지_않는다() {
        when(gate.select(INSTITUTION)).thenThrow(
            new CredentialSigningException(CredentialSigningException.KEY_COMPROMISED, Disposition.FAIL));

        assertThatThrownBy(() -> signer.sign(CREDENTIAL,
            () -> { throw new AssertionError("context must not be built before the gate"); }, HASHER, NOW))
            .isInstanceOfSatisfying(CredentialSigningException.class, exception -> {
                assertThat(exception.code()).isEqualTo(CredentialSigningException.KEY_COMPROMISED);
                assertThat(exception.disposition()).isEqualTo(Disposition.FAIL);
            });
        assertThat(transit.requestedVersions).isEmpty();
    }

    @Test
    void 확정_뒤_행이_ISSUING을_벗어나면_오래된_응답으로_버린다() {
        InstitutionSignKey master = master(2);
        signing.preCommit(master.id(), 2, new CredentialSigningInput("aGVhZGVy", "cGF5bG9hZA"));
        signing.vcPayload = "{\"mediaType\":\"application/vc+jwt\",\"compactJws\":\"aGVhZGVy.cGF5bG9hZA.c2ln\"}";
        signing.vcHash = HASH;
        signing.credentialStatusOverride = CredentialStatus.ISSUED;

        assertFailure(CredentialSigningException.STALE, Disposition.STALE);
    }

    @Test
    void 이미_봉투가_저장됐으면_다시_서명하지_않는다() {
        InstitutionSignKey master = master(2);
        signing.preCommit(master.id(), 2, new CredentialSigningInput("aGVhZGVy", "cGF5bG9hZA"));
        signing.vcPayload = "{\"mediaType\":\"application/vc+jwt\",\"compactJws\":\"aGVhZGVy.cGF5bG9hZA.c2ln\"}";
        signing.vcHash = HASH;

        CredentialIssuanceSigner.Signed signed = signer.sign(CREDENTIAL, this::context, HASHER, NOW);

        assertThat(signed.envelope().compactJws()).isEqualTo("aGVhZGVy.cGF5bG9hZA.c2ln");
        assertThat(transit.requestedVersions).isEmpty();
    }

    private CredentialFabricIssuanceContext context() {
        return new CredentialFabricIssuanceContext(
            new CredentialDocument("{\"id\":\"doc\"}"), "urn:issuer", "urn:subject", NOW);
    }

    private void assertFailure(String code, Disposition disposition) {
        assertThatThrownBy(() -> signer.sign(CREDENTIAL, this::context, HASHER, NOW))
            .isInstanceOfSatisfying(CredentialSigningException.class, exception -> {
                assertThat(exception.code()).isEqualTo(code);
                assertThat(exception.disposition()).isEqualTo(disposition);
            });
    }

    private InstitutionSignKey master(int currentVersion) {
        InstitutionSignKey key = InstitutionSignKey.provisioning(
            UUID.randomUUID(), INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, NOW).activate(1, NOW);
        for (int version = 2; version <= currentVersion; version++) {
            key = key.rotated(version, NOW);
        }
        when(keys.findById(key.id())).thenReturn(Optional.of(key));
        return key;
    }

    private InstitutionSignKeyVersion version(InstitutionSignKey master, int keyVersion) {
        InstitutionSignKeyVersion version = InstitutionSignKeyVersion.active(UUID.randomUUID(), master.id(),
            INSTITUTION, SignKeyPurpose.CREDENTIAL, 1, keyVersion, JWK, NOW, null, NOW);
        when(versions.findBySignKeyIdAndVersion(master.id(), keyVersion)).thenReturn(Optional.of(version));
        return version;
    }

    private void select(InstitutionSignKey master, InstitutionSignKeyVersion version) {
        when(gate.select(INSTITUTION)).thenReturn(selection(master, version));
    }

    private static SigningKeySelection selection(InstitutionSignKey master, InstitutionSignKeyVersion version) {
        return new SigningKeySelection(master, version, ISSUER + "#" + version.kidFragment());
    }

    private static String b64(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String segment) {
        return new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
    }

    private static byte[] filled(int length, byte value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static final class ScriptedTransit implements TransitSigningPort {
        final List<Integer> requestedVersions = new ArrayList<>();
        final List<byte[]> inputs = new ArrayList<>();
        final Deque<IntFunction<TransitSignature>> script = new ArrayDeque<>();
        Runnable duringSign = () -> { };

        @Override
        public TransitSignature sign(String mount, String keyName, int keyVersion, byte[] signingInput) {
            requestedVersions.add(keyVersion);
            inputs.add(signingInput);
            duringSign.run();
            return script.isEmpty() ? new TransitSignature(keyVersion, SIGNATURE) : script.poll().apply(keyVersion);
        }
    }

    /** tb_credentials 한 행. 조건은 CredentialSigningMapper.xml 과 같다. */
    private static final class FakeSigning implements CredentialSigningRepository {
        CredentialStatus status = CredentialStatus.ISSUING;
        UUID signKeyId;
        Integer signKeyVersion;
        Integer attemptNo;
        String header;
        String payload;
        String issuer;
        String subject;
        Instant issuedAt;
        String vcPayload;
        String vcHash;
        int recommits;
        CredentialStatus credentialStatusOverride;

        void preCommit(UUID keyId, int version, CredentialSigningInput input) {
            signKeyId = keyId;
            signKeyVersion = version;
            attemptNo = 1;
            header = input.encodedHeader();
            payload = input.encodedPayload();
            issuer = "urn:issuer";
            subject = "urn:subject";
            issuedAt = NOW;
        }

        @Override public Optional<CredentialSigningState> findSigningState(UUID credentialId) {
            return Optional.of(new CredentialSigningState(credentialId, status, signKeyId, signKeyVersion,
                attemptNo, header, payload, vcPayload));
        }

        @Override public Optional<UUID> findInstitutionId(UUID credentialId) {
            return Optional.of(INSTITUTION);
        }

        @Override public boolean commitSigningInput(UUID credentialId, UUID keyId, int version,
            CredentialSigningInput input, String issuerIdentifier, String subjectIdentifier,
            Instant issued, Instant now) {
            if (status != CredentialStatus.ISSUING || vcPayload != null || header != null) {
                return false;
            }
            preCommit(keyId, version, input);
            issuer = issuerIdentifier;
            subject = subjectIdentifier;
            issuedAt = issued;
            return true;
        }

        @Override public boolean recommitHeader(UUID credentialId, int expectedAttemptNo, UUID keyId,
            int version, CredentialSigningInput input, Instant now) {
            if (status != CredentialStatus.ISSUING || vcPayload != null
                || !Integer.valueOf(expectedAttemptNo).equals(attemptNo)
                || !input.encodedPayload().equals(payload)) {
                return false;
            }
            recommits++;
            signKeyId = keyId;
            signKeyVersion = version;
            header = input.encodedHeader();
            attemptNo = attemptNo + 1;
            return true;
        }

        @Override public boolean storeSignature(UUID credentialId, int attemptNumber, UUID keyId, int version,
            CredentialSigningInput input, String envelope, String hash, Instant now) {
            if (status != CredentialStatus.ISSUING || vcPayload != null
                || !Integer.valueOf(attemptNumber).equals(attemptNo) || !keyId.equals(signKeyId)
                || !Integer.valueOf(version).equals(signKeyVersion)
                || !input.encodedHeader().equals(header) || !input.encodedPayload().equals(payload)) {
                return false;
            }
            vcPayload = envelope;
            vcHash = hash;
            return true;
        }

        CredentialRepository credentials() {
            return new CredentialRepository() {
                @Override public Optional<Credential> findById(UUID id) {
                    CredentialStatus effectiveStatus =
                        credentialStatusOverride != null ? credentialStatusOverride : status;
                    return Optional.of(new Credential(id, UUID.fromString("90000000-0000-0000-0000-000000000009"),
                        null, "CERT-1", 1, issuer, subject, "LIFELONG_EDUCATION_COMPLETION", effectiveStatus,
                        issuedAt, null, vcPayload, vcHash, null, null, issuedAt, null, null, null, null, NOW, NOW));
                }
                @Override public Optional<Credential> findByCredentialNo(String no) { throw new UnsupportedOperationException(); }
                @Override public Optional<Credential> findByCredentialHash(String hash) { throw new UnsupportedOperationException(); }
                @Override public Optional<Credential> findByIdForUpdate(UUID id) { throw new UnsupportedOperationException(); }
                @Override public Optional<Credential> findActiveByGroupId(UUID id) { throw new UnsupportedOperationException(); }
                @Override public List<Credential> findByUserId(UUID id, int limit, int offset, String sort) { throw new UnsupportedOperationException(); }
                @Override public long countByUserId(UUID id) { throw new UnsupportedOperationException(); }
                @Override public int nextVersionForUpdate(UUID id) { throw new UnsupportedOperationException(); }
                @Override public void insert(Credential value) { throw new UnsupportedOperationException(); }
                @Override public boolean insertIfChainKeyAvailable(Credential value) { throw new UnsupportedOperationException(); }
                @Override public void update(Credential value) { throw new UnsupportedOperationException(); }
            };
        }
    }
}
