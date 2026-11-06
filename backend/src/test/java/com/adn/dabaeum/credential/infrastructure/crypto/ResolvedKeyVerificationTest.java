package com.adn.dabaeum.credential.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.application.BitstringStatusListEncoder;
import com.adn.dabaeum.credential.application.CredentialDocumentFactory;
import com.adn.dabaeum.credential.application.CredentialStatusListDocumentFactory;
import com.adn.dabaeum.credential.application.DabaeumUrnCredentialIdentifierProvider;
import com.adn.dabaeum.credential.application.DefaultCredentialUriProvider;
import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier.VerificationFailure;
import com.adn.dabaeum.credential.domain.CredentialSignatureVerifier.VerifiedDocument;
import com.adn.dabaeum.credential.domain.CredentialSigningInput;
import com.adn.dabaeum.credential.domain.CredentialStatusList;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.CredentialVerificationKeyResolver;
import com.adn.dabaeum.credential.domain.CredentialVerificationKeyResolver.KeyResolutionException;
import com.adn.dabaeum.credential.domain.SignKeyVersionStatus;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import com.adn.dabaeum.credential.domain.VerificationKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

/** 설계서 §9 2~4단계: kid 로 해석한 버전 행 공개키로 서명을 검증한다. 형식 규칙은 파일 키 검증과 같다. */
class ResolvedKeyVerificationTest {

    private static final String LEGACY_KEY_ID = "urn:dabaeum:credential-key:development-1";
    private static final UUID INSTITUTION = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OTHER = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID SIGN_KEY = UUID.fromString("90000000-0000-0000-0000-000000000009");
    private static final CredentialUriProvider URIS = new DefaultCredentialUriProvider(
        new VcProperties("https://vc.example.test", "v1", "development-1"));
    private static final String ISSUER = URIS.issuerUrl(INSTITUTION);
    private static final String LEGACY_PAYLOAD =
        "{\"@context\":[\"https://www.w3.org/ns/credentials/v2\"],"
            + "\"id\":\"urn:uuid:10000000-0000-0000-0000-000000000001\","
            + "\"type\":[\"VerifiableCredential\",\"LifelongEducationCompletionCredential\"],"
            + "\"issuer\":\"urn:dabaeum:institution:20000000-0000-0000-0000-000000000002\","
            + "\"validFrom\":\"2026-08-05T01:02:03Z\","
            + "\"validUntil\":\"2027-08-05T01:02:03Z\","
            + "\"credentialSubject\":{"
            + "\"id\":\"urn:dabaeum:user:30000000-0000-0000-0000-000000000003\","
            + "\"completionId\":\"40000000-0000-0000-0000-000000000004\","
            + "\"enrollmentId\":\"50000000-0000-0000-0000-000000000005\","
            + "\"courseId\":\"60000000-0000-0000-0000-000000000006\","
            + "\"completedAt\":\"2026-08-04T10:20:30Z\","
            + "\"attendanceRate\":90.00,\"completedMinutes\":1200,\"creditValue\":3.00}}";

    private final Ed25519JoseCredentialProofService keyless =
        Ed25519JoseCredentialProofService.signingInputOnly(LEGACY_KEY_ID, URIS, new ObjectMapper());
    private final RecordingResolver resolver = new RecordingResolver();
    private KeyPair keys;

    @BeforeEach
    void setUp() throws Exception {
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void 버전_조각_kid_를_기관_버전_행_키로_검증하고_키를_돌려준다() throws Exception {
        CredentialDocument document = credentialDocument();
        SignedCredentialEnvelope signed = sign(
            keyless.credentialSigningInput(document, INSTITUTION, ISSUER + "#v2"), keys.getPrivate());
        resolver.key = new VerificationKey(keys.getPublic(), "v2", SIGN_KEY, 2, SignKeyVersionStatus.ACTIVE, null);

        VerifiedDocument verified = keyless.verifyCredential(signed, resolver);

        assertThat(verified.document()).isEqualTo(document);
        assertThat(verified.institutionId()).isEqualTo(INSTITUTION);
        assertThat(verified.key()).isSameAs(resolver.key);
        assertThat(resolver.requests).containsExactly(INSTITUTION + "|v2|CREDENTIAL");
    }

    @Test
    void 유출_버전도_서명이_맞으면_검증하고_판정은_호출자에게_넘긴다() throws Exception {
        SignedCredentialEnvelope signed = sign(
            keyless.credentialSigningInput(credentialDocument(), INSTITUTION, ISSUER + "#v1"), keys.getPrivate());
        resolver.key = new VerificationKey(keys.getPublic(), "v1", SIGN_KEY, 1,
            SignKeyVersionStatus.COMPROMISED, Instant.parse("2026-09-01T00:00:00Z"));

        assertThat(keyless.verifyCredential(signed, resolver).key().compromised()).isTrue();
    }

    @Test
    void 파일_키_시절_kid_와_전환_이전_프로필은_development_1_로_해석한다() throws Exception {
        Ed25519JoseCredentialProofService fileKey = new Ed25519JoseCredentialProofService(
            LEGACY_KEY_ID, URIS, keys.getPrivate(), keys.getPublic(), new ObjectMapper());
        resolver.key = VerificationKey.legacyFile(keys.getPublic());

        keyless.verifyCredential(fileKey.sign(credentialDocument()), resolver);
        keyless.verifyCredential(signRaw(header(LEGACY_KEY_ID), LEGACY_PAYLOAD, keys.getPrivate()), resolver);

        assertThat(resolver.requests).containsExactly(
            INSTITUTION + "|development-1|CREDENTIAL", INSTITUTION + "|development-1|CREDENTIAL");
    }

    @Test
    void 발급_기관_URL_조각이_아닌_kid_는_키를_찾기_전에_거부한다() throws Exception {
        String payload = credentialDocument().canonicalJson();
        resolver.key = new VerificationKey(keys.getPublic(), "v1", SIGN_KEY, 1, SignKeyVersionStatus.ACTIVE, null);

        for (String kid : List.of(URIS.issuerUrl(OTHER) + "#v1", LEGACY_KEY_ID, ISSUER + "#", ISSUER)) {
            SignedCredentialEnvelope signed = signRaw(header(kid), payload, keys.getPrivate());
            assertFailure(() -> keyless.verifyCredential(signed, resolver),
                KeyResolutionException.ISSUER_MISMATCH);
        }
        SignedCredentialEnvelope legacyWithVersionKid =
            signRaw(header(ISSUER + "#v1"), LEGACY_PAYLOAD, keys.getPrivate());
        assertFailure(() -> keyless.verifyCredential(legacyWithVersionKid, resolver),
            KeyResolutionException.ISSUER_MISMATCH);
        assertThat(resolver.requests).isEmpty();
    }

    @Test
    void 해석기의_목적_불일치와_사용_불가를_코드로_전한다() throws Exception {
        SignedCredentialEnvelope signed = sign(
            keyless.credentialSigningInput(credentialDocument(), INSTITUTION, ISSUER + "#status-v1"),
            keys.getPrivate());

        resolver.failure = KeyResolutionException.PURPOSE_MISMATCH;
        assertFailure(() -> keyless.verifyCredential(signed, resolver), KeyResolutionException.PURPOSE_MISMATCH);

        resolver.failure = KeyResolutionException.UNAVAILABLE;
        assertThatThrownBy(() -> keyless.verifyCredential(signed, resolver))
            .isInstanceOfSatisfying(VerificationFailure.class,
                failure -> assertThat(failure.unavailable()).isTrue());

        resolver.failure = null;
        resolver.runtimeFailure = true;
        assertFailure(() -> keyless.verifyCredential(signed, resolver), KeyResolutionException.UNAVAILABLE);
    }

    @Test
    void 다른_키의_서명과_비정규형과_잘못된_미디어_타입은_서명_무효다() throws Exception {
        CredentialDocument document = credentialDocument();
        SignedCredentialEnvelope signed = sign(
            keyless.credentialSigningInput(document, INSTITUTION, ISSUER + "#v2"), keys.getPrivate());
        resolver.key = new VerificationKey(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic(),
            "v2", SIGN_KEY, 2, SignKeyVersionStatus.ACTIVE, null);
        assertFailure(() -> keyless.verifyCredential(signed, resolver), VerificationFailure.PROOF_INVALID);

        resolver.requests.clear();
        String spaced = document.canonicalJson().replace(",\"id\":", ", \"id\":");
        assertFailure(() -> keyless.verifyCredential(
            signRaw(header(ISSUER + "#v2"), spaced, keys.getPrivate()), resolver), VerificationFailure.PROOF_INVALID);
        assertFailure(() -> keyless.verifyCredential(
            new SignedCredentialEnvelope("application/json", signed.compactJws()), resolver),
            VerificationFailure.PROOF_INVALID);
        assertThat(resolver.requests).isEmpty();
    }

    @Test
    void Status_List_는_STATUS_LIST_목적으로_해석하고_수료증_검증에는_통과하지_않는다() throws Exception {
        CredentialDocument statusList = statusListDocument();
        SignedCredentialEnvelope signed =
            signRaw(header(ISSUER + "#status-v1"), statusList.canonicalJson(), keys.getPrivate());
        resolver.key = new VerificationKey(keys.getPublic(), "status-v1", SIGN_KEY, 1,
            SignKeyVersionStatus.ACTIVE, null);

        assertThat(keyless.verifyStatusList(signed, resolver).document()).isEqualTo(statusList);
        assertThat(resolver.requests).containsExactly(INSTITUTION + "|status-v1|STATUS_LIST");
        assertFailure(() -> keyless.verifyCredential(signed, resolver), VerificationFailure.PROOF_INVALID);
    }

    @Test
    void 설정은_키_파일과_해석기_없이_검증기를_만들고_해석기가_없으면_사용_불가로_실패한다() throws Exception {
        SignedCredentialEnvelope signed = sign(
            keyless.credentialSigningInput(credentialDocument(), INSTITUTION, ISSUER + "#v1"), keys.getPrivate());

        new ApplicationContextRunner()
            .withUserConfiguration(CredentialProofConfiguration.class)
            .withBean(CredentialUriProvider.class, () -> URIS)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .run(context -> {
                assertThat(context).hasSingleBean(CredentialSignatureVerifier.class);
                assertFailure(() -> context.getBean(CredentialSignatureVerifier.class).verifyCredential(signed),
                    KeyResolutionException.UNAVAILABLE);
            });
    }

    private static CredentialDocument credentialDocument() {
        return new CredentialDocumentFactory(new ObjectMapper(), new DabaeumUrnCredentialIdentifierProvider(URIS))
            .create(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                new CredentialStatusListEntry(UUID.fromString("70000000-0000-0000-0000-000000000007"), 94_567),
                INSTITUTION,
                UUID.fromString("40000000-0000-0000-0000-000000000004"),
                UUID.fromString("50000000-0000-0000-0000-000000000005"),
                UUID.fromString("60000000-0000-0000-0000-000000000006"),
                UUID.fromString("80000000-0000-0000-0000-000000000008"),
                Instant.parse("2026-09-01T00:00:00Z"), null, Instant.parse("2026-08-31T00:00:00Z"),
                new BigDecimal("95.50"), 1200, null);
    }

    private static CredentialDocument statusListDocument() {
        return new CredentialStatusListDocumentFactory(
            new ObjectMapper(), new DabaeumUrnCredentialIdentifierProvider(URIS)).create(
                UUID.fromString("70000000-0000-0000-0000-000000000007"), INSTITUTION,
                Instant.parse("2026-09-03T01:02:03Z"),
                new BitstringStatusListEncoder().encode(CredentialStatusList.MINIMUM_CAPACITY, List.of(12)));
    }

    private static String header(String kid) {
        return "{\"alg\":\"EdDSA\",\"cty\":\"vc\",\"kid\":\"" + kid + "\",\"typ\":\"vc+jwt\"}";
    }

    private static SignedCredentialEnvelope signRaw(String header, String payload, PrivateKey privateKey)
        throws Exception {
        return sign(new CredentialSigningInput(encode(header), encode(payload)), privateKey);
    }

    private static SignedCredentialEnvelope sign(CredentialSigningInput input, PrivateKey privateKey)
        throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(input.bytes());
        return input.envelope(signer.sign());
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertFailure(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(VerificationFailure.class,
            failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static final class RecordingResolver implements CredentialVerificationKeyResolver {
        private final List<String> requests = new ArrayList<>();
        private VerificationKey key;
        private String failure;
        private boolean runtimeFailure;

        @Override
        public VerificationKey resolve(UUID institutionId, String kidFragment, DocumentKind kind) {
            requests.add(institutionId + "|" + kidFragment + "|" + kind);
            if (runtimeFailure) {
                throw new IllegalStateException("database unavailable");
            }
            if (failure != null) {
                throw new KeyResolutionException(failure);
            }
            return key;
        }
    }
}
