package com.adn.dabaeum.credential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier.VerificationFailure;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier.VerifiedDocument;
import com.adn.dabaeum.credential.domain.CredentialStatusList;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import com.adn.dabaeum.credential.domain.StatusListSigningPort;
import com.adn.dabaeum.credential.domain.TransitSignature;
import com.adn.dabaeum.credential.domain.TransitSigningException;
import com.adn.dabaeum.credential.domain.TransitSigningException.Reason;
import com.adn.dabaeum.credential.domain.VerificationKey;
import com.adn.dabaeum.credential.infrastructure.crypto.Ed25519JoseCredentialProofService;
import com.adn.dabaeum.credential.infrastructure.crypto.Ed25519PublicJwkFactory;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 설계서 §9.1: Status List 는 기관별 STATUS_LIST 키의 현재 ACTIVE 버전을 명시해 상태 역할로 서명한다. */
class TransitCredentialStatusListProofServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private static final String LEGACY_KEY_ID = "urn:dabaeum:credential-key:development-1";
    private static final UUID INSTITUTION = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String JWK =
        "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\"AA\"}";
    private static final CredentialUriProvider URIS = new DefaultCredentialUriProvider(
        new VcProperties("https://vc.example.test", "v1", "development-1"));
    private static final byte[] SIGNATURE = new byte[64];

    static {
        Arrays.fill(SIGNATURE, (byte) 7);
    }

    private final DefaultCredentialVerificationKeyResolverTest.FakeKeys keys =
        new DefaultCredentialVerificationKeyResolverTest.FakeKeys();
    private final DefaultCredentialVerificationKeyResolverTest.FakeVersions versions =
        new DefaultCredentialVerificationKeyResolverTest.FakeVersions();
    private final ScriptedStatusTransit transit = new ScriptedStatusTransit();
    private final CredentialSignatureVerifier verifier = mock(CredentialSignatureVerifier.class);
    private final Ed25519JoseCredentialProofService keyless =
        Ed25519JoseCredentialProofService.signingInputOnly(LEGACY_KEY_ID, URIS, new ObjectMapper());
    private final TransitCredentialStatusListProofService service = new TransitCredentialStatusListProofService(
        keys, versions, URIS, keyless::statusListSigningInput, transit, verifier);

    @Test
    void 현재_ACTIVE_버전의_kid_로_헤더를_만들고_그_버전을_명시해_상태_mount_에_서명한다() {
        InstitutionSignKey master = statusMaster(2);
        versions.rows.add(row(master, 2));

        SignedCredentialEnvelope envelope = service.signStatusList(statusListDocument());

        assertThat(master.mount()).isEqualTo("education-status");
        assertThat(transit.requests).containsExactly(master.mount() + "|" + master.keyName() + "|2");
        String[] segments = envelope.compactJws().split("\\.", -1);
        assertThat(decode(segments[0])).isEqualTo("{\"alg\":\"EdDSA\",\"cty\":\"vc\",\"kid\":\""
            + URIS.issuerUrl(INSTITUTION) + "#status-v2\",\"typ\":\"vc+jwt\"}");
        assertThat(decode(segments[1])).isEqualTo(statusListDocument().canonicalJson());
        assertThat(segments[2]).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(SIGNATURE));
        assertThat(transit.inputs.get(0))
            .isEqualTo((segments[0] + "." + segments[1]).getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void 서명할_수_있는_STATUS_LIST_키가_없으면_OpenBao_를_부르지_않고_실패한다() {
        assertGenerationFailed(() -> service.signStatusList(statusListDocument()));

        keys.rows.add(InstitutionSignKey.provisioning(
            UUID.randomUUID(), INSTITUTION, SignKeyPurpose.STATUS_LIST, 1, NOW));
        assertGenerationFailed(() -> service.signStatusList(statusListDocument()));

        keys.rows.clear();
        InstitutionSignKey master = statusMaster(2);
        versions.rows.add(row(master, 2).retire(NOW));
        assertGenerationFailed(() -> service.signStatusList(statusListDocument()));

        assertThat(transit.requests).isEmpty();
    }

    @Test
    void 응답_버전이_요청과_다르면_실패한다() {
        InstitutionSignKey master = statusMaster(1);
        versions.rows.add(row(master, 1));
        transit.script.add(version -> new TransitSignature(version + 1, SIGNATURE));

        assertGenerationFailed(() -> service.signStatusList(statusListDocument()));
    }

    @Test
    void 버전_거부는_DB_에서_다시_골라_한_번만_재시도한다() {
        InstitutionSignKey master = statusMaster(1);
        versions.rows.add(row(master, 1));
        transit.script.add(version -> {
            // 회전 T2 커밋과 min_encryption_version 사이에 v1 을 골랐다.
            keys.rows.replaceAll(key -> key.id().equals(master.id()) ? key.rotated(2, NOW) : key);
            versions.rows.add(row(master, 2));
            throw rejected(Reason.VERSION_REJECTED);
        });

        SignedCredentialEnvelope envelope = service.signStatusList(statusListDocument());

        assertThat(transit.requests).containsExactly(
            master.mount() + "|" + master.keyName() + "|1", master.mount() + "|" + master.keyName() + "|2");
        assertThat(decode(envelope.compactJws().split("\\.", -1)[0])).contains("#status-v2");
    }

    @Test
    void 두_번째_버전_거부와_일시_장애와_거부는_실패로_끝난다() {
        InstitutionSignKey master = statusMaster(1);
        versions.rows.add(row(master, 1));
        transit.script.add(version -> { throw rejected(Reason.VERSION_REJECTED); });
        transit.script.add(version -> { throw rejected(Reason.VERSION_REJECTED); });

        assertGenerationFailed(() -> service.signStatusList(statusListDocument()));
        assertThat(transit.requests).hasSize(2);

        for (Reason reason : List.of(Reason.UNAVAILABLE, Reason.REJECTED)) {
            transit.requests.clear();
            transit.script.add(version -> { throw rejected(reason); });
            assertGenerationFailed(() -> service.signStatusList(statusListDocument()));
            assertThat(transit.requests).hasSize(1);
        }
    }

    @Test
    void 플랫폼_기관_issuer_가_아니거나_Status_List_가_아닌_문서는_서명하지_않는다() {
        InstitutionSignKey master = statusMaster(1);
        versions.rows.add(row(master, 1));

        assertGenerationFailed(() -> service.signStatusList(new CredentialDocument(
            "{\"issuer\":\"https://evil.example/api/v1/vc/issuers/" + INSTITUTION + "\"}")));
        assertGenerationFailed(() -> service.signStatusList(new CredentialDocument(
            "{\"issuer\":\"" + URIS.issuerUrl(INSTITUTION) + "\"}")));
        assertThat(transit.requests).isEmpty();
    }

    @Test
    void Status_List_검증은_버전_행_키_검증기에_맡기고_실패는_무효_증명으로_바꾼다() throws Exception {
        SignedCredentialEnvelope envelope = new SignedCredentialEnvelope("application/vc+jwt", "a.b.c");
        CredentialDocument document = statusListDocument();
        when(verifier.verifyStatusList(envelope)).thenReturn(new VerifiedDocument(document, INSTITUTION,
            VerificationKey.legacyFile(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic())));

        assertThat(service.verifyStatusList(envelope)).isEqualTo(document);

        when(verifier.verifyStatusList(envelope))
            .thenThrow(new VerificationFailure(VerificationFailure.PROOF_INVALID));
        assertThatThrownBy(() -> service.verifyStatusList(envelope))
            .isInstanceOfSatisfying(CredentialProofService.ProofException.class, exception ->
                assertThat(exception.failureCode())
                    .isEqualTo(CredentialProofService.FailureCode.CREDENTIAL_PROOF_INVALID));
    }

    /**
     * 리뷰 지적 사항: Task 6 서명 결과(kid {@code #status-v{n}}, STATUS_LIST 목적, signing input)가 Task 3
     * 검증기·실 해석기(DefaultCredentialVerificationKeyResolver)로 끝까지 검증되는지 실제 Ed25519 서명으로 확인한다.
     */
    @Test
    void 실제_Transit_서명_결과는_실_해석기와_검증기로_끝까지_검증된다() throws Exception {
        KeyPair signingKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        InstitutionSignKey master = statusMaster(1);
        versions.rows.add(InstitutionSignKeyVersion.active(UUID.randomUUID(), master.id(), INSTITUTION,
            SignKeyPurpose.STATUS_LIST, master.generation(), 1,
            DefaultCredentialVerificationKeyResolverTest.storedJwk(signingKeys.getPublic()), NOW, null, NOW));
        TransitCredentialStatusListProofService realService = new TransitCredentialStatusListProofService(
            keys, versions, URIS, keyless::statusListSigningInput,
            new RealSigningTransit(signingKeys.getPrivate()), verifier);
        CredentialDocument document = statusListDocument();

        SignedCredentialEnvelope envelope = realService.signStatusList(document);

        DefaultCredentialVerificationKeyResolver resolver = new DefaultCredentialVerificationKeyResolver(
            versions, keys, new Ed25519PublicJwkFactory(), (Supplier<PublicKey>) () -> null);
        VerifiedDocument verified = keyless.verifyStatusList(envelope, resolver);
        assertThat(verified.institutionId()).isEqualTo(INSTITUTION);
        assertThat(verified.key().kidFragment()).isEqualTo("status-v1");
        assertThat(verified.key().keyVersion()).isEqualTo(1);
        assertThat(verified.document().canonicalJson()).isEqualTo(document.canonicalJson());

        DefaultCredentialVerificationKeyResolverTest.FakeKeys wrongKeys =
            new DefaultCredentialVerificationKeyResolverTest.FakeKeys();
        wrongKeys.rows.add(master);
        DefaultCredentialVerificationKeyResolverTest.FakeVersions wrongVersions =
            new DefaultCredentialVerificationKeyResolverTest.FakeVersions();
        wrongVersions.rows.add(InstitutionSignKeyVersion.active(UUID.randomUUID(), master.id(), INSTITUTION,
            SignKeyPurpose.STATUS_LIST, master.generation(), 1,
            DefaultCredentialVerificationKeyResolverTest.storedJwk(
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic()), NOW, null, NOW));
        DefaultCredentialVerificationKeyResolver wrongResolver = new DefaultCredentialVerificationKeyResolver(
            wrongVersions, wrongKeys, new Ed25519PublicJwkFactory(), (Supplier<PublicKey>) () -> null);

        assertThatThrownBy(() -> keyless.verifyStatusList(envelope, wrongResolver))
            .isInstanceOfSatisfying(VerificationFailure.class,
                failure -> assertThat(failure.code()).isEqualTo(VerificationFailure.PROOF_INVALID));
    }

    private InstitutionSignKey statusMaster(int currentVersion) {
        InstitutionSignKey key = InstitutionSignKey.provisioning(
            UUID.randomUUID(), INSTITUTION, SignKeyPurpose.STATUS_LIST, 1, NOW).activate(1, NOW);
        for (int version = 2; version <= currentVersion; version++) {
            key = key.rotated(version, NOW);
        }
        keys.rows.add(key);
        return key;
    }

    private static InstitutionSignKeyVersion row(InstitutionSignKey master, int keyVersion) {
        return InstitutionSignKeyVersion.active(UUID.randomUUID(), master.id(), INSTITUTION,
            SignKeyPurpose.STATUS_LIST, master.generation(), keyVersion, JWK, NOW, null, NOW);
    }

    private static CredentialDocument statusListDocument() {
        return new CredentialStatusListDocumentFactory(
            new ObjectMapper(), new DabaeumUrnCredentialIdentifierProvider(URIS)).create(
                UUID.fromString("70000000-0000-0000-0000-000000000007"), INSTITUTION,
                Instant.parse("2026-09-03T01:02:03Z"),
                new BitstringStatusListEncoder().encode(CredentialStatusList.MINIMUM_CAPACITY, List.of(12)));
    }

    private static TransitSigningException rejected(Reason reason) {
        return new TransitSigningException(reason, "scripted", null);
    }

    private static String decode(String segment) {
        return new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
    }

    private static void assertGenerationFailed(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CredentialProofService.ProofException.class,
            exception -> assertThat(exception.failureCode())
                .isEqualTo(CredentialProofService.FailureCode.CREDENTIAL_PROOF_GENERATION_FAILED));
    }

    /** 실제 개인키로 서명한다 — 검증이 서명을 실제로 확인하는지(위조 거부까지) 보려면 스크립트가 아니라 진짜 서명이 필요하다. */
    private static final class RealSigningTransit implements StatusListSigningPort {
        private final PrivateKey privateKey;

        RealSigningTransit(PrivateKey privateKey) {
            this.privateKey = privateKey;
        }

        @Override
        public TransitSignature sign(String mount, String keyName, int keyVersion, byte[] input) {
            try {
                Signature signer = Signature.getInstance("Ed25519");
                signer.initSign(privateKey);
                signer.update(input);
                return new TransitSignature(keyVersion, signer.sign());
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class ScriptedStatusTransit implements StatusListSigningPort {
        private final List<String> requests = new ArrayList<>();
        private final List<byte[]> inputs = new ArrayList<>();
        private final Deque<IntFunction<TransitSignature>> script = new ArrayDeque<>();

        @Override
        public TransitSignature sign(String mount, String keyName, int keyVersion, byte[] input) {
            requests.add(mount + "|" + keyName + "|" + keyVersion);
            inputs.add(input.clone());
            IntFunction<TransitSignature> next = script.poll();
            return next == null ? new TransitSignature(keyVersion, SIGNATURE) : next.apply(keyVersion);
        }
    }
}
