package com.adn.dabaeum.credential.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.application.CredentialDocumentFactory;
import com.adn.dabaeum.credential.application.CredentialStatusListDocumentFactory;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.application.DabaeumUrnCredentialIdentifierProvider;
import com.adn.dabaeum.credential.application.DefaultCredentialUriProvider;
import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class Ed25519JoseCredentialProofServiceTest {

    private static final String KEY_ID = "urn:dabaeum:credential-key:development-1";
    private static final String ISSUER = "https://vc.example.test/api/v1/vc/issuers/"
        + "20000000-0000-0000-0000-000000000002";
    private static final String NEW_KEY_ID = ISSUER + "#development-1";
    private static final String EXACT_HEADER = "{\"alg\":\"EdDSA\",\"cty\":\"vc\","
        + "\"kid\":\"" + NEW_KEY_ID + "\",\"typ\":\"vc+jwt\"}";
    private static final String LEGACY_HEADER = "{\"alg\":\"EdDSA\",\"cty\":\"vc\","
        + "\"kid\":\"" + KEY_ID + "\",\"typ\":\"vc+jwt\"}";
    private static final CredentialUriProvider URI_PROVIDER = new DefaultCredentialUriProvider(
        new VcProperties("https://vc.example.test", "v1", "development-1"));
    private static final UUID LIST_ID = UUID.fromString("70000000-0000-0000-0000-000000000007");
    private static final String LIST_URL =
        "https://vc.example.test/api/v1/vc/status-lists/70000000-0000-0000-0000-000000000007";
    private static final CredentialStatusListEntry STATUS_ENTRY =
        new CredentialStatusListEntry(LIST_ID, 94_567);
    private static final String LEGACY_STATUS =
        "\"credentialStatus\":{\"id\":\"https://vc.example.test/api/v1/vc/status/"
            + "CERT-LEGACY-1\",\"type\":\"DabaeumCredentialStatus\"}";

    private KeyPair keyPair;
    private Ed25519JoseCredentialProofService service;
    private String validPayload;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        service = new Ed25519JoseCredentialProofService(
            KEY_ID, URI_PROVIDER, keyPair.getPrivate(), keyPair.getPublic(), new ObjectMapper());
        validPayload = validDocument().canonicalJson();
    }

    @Test
    void signsDeterministicallyWithExactJoseHeaderAndVerifiesRoundTrip() {
        CredentialDocument document = validDocument();

        SignedCredentialEnvelope first = service.sign(document);
        SignedCredentialEnvelope second = service.sign(document);

        assertThat(first).isEqualTo(second);
        assertThat(first.mediaType()).isEqualTo("application/vc+jwt");
        assertThat(first.compactJws().split("\\.", -1)).hasSize(3);
        assertThat(decode(first.compactJws().split("\\.", -1)[0])).isEqualTo(EXACT_HEADER);
        assertThat(first.compactJws()).doesNotContain("=");
        assertThat(service.verify(first)).isEqualTo(document);
    }

    @Test
    void verifiesLegacyProfileWithoutRewritingItsStoredPayload() throws Exception {
        String legacyPayload = legacyPayload();
        SignedCredentialEnvelope legacy = signRaw(
            LEGACY_HEADER, legacyPayload, keyPair.getPrivate());

        assertThat(service.verify(legacy).canonicalJson()).isEqualTo(legacyPayload);
    }

    @Test
    void rejectsMalformedSegmentsAndPayloadOrSignatureTampering() {
        SignedCredentialEnvelope signed = service.sign(validDocument());
        String[] segments = signed.compactJws().split("\\.", -1);

        assertInvalid(new SignedCredentialEnvelope(signed.mediaType(), "only.two"));
        assertInvalid(new SignedCredentialEnvelope(signed.mediaType(),
            segments[0] + "." + segments[1] + "=." + segments[2]));
        assertInvalid(new SignedCredentialEnvelope(signed.mediaType(),
            segments[0] + "." + mutate(segments[1]) + "." + segments[2]));
        assertInvalid(new SignedCredentialEnvelope(signed.mediaType(),
            segments[0] + "." + segments[1] + "." + mutate(segments[2])));
    }

    @Test
    void rejectsWrongPublicKeyAndWrongMediaType() throws Exception {
        SignedCredentialEnvelope signed = service.sign(validDocument());
        KeyPair other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var wrongVerifier = new Ed25519JoseCredentialProofService(
            KEY_ID, URI_PROVIDER, other.getPrivate(), other.getPublic(), new ObjectMapper());

        assertThatThrownBy(() -> wrongVerifier.verify(signed))
            .isInstanceOfSatisfying(CredentialProofService.ProofException.class,
                exception -> assertThat(exception.failureCode()).isEqualTo(
                    CredentialProofService.FailureCode.CREDENTIAL_PROOF_INVALID));
        assertInvalid(new SignedCredentialEnvelope("application/json", signed.compactJws()));
    }

    @Test
    void mapsSigningFailureToSafeGenerationCode() throws Exception {
        KeyPair incompatible = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var incompatibleService = new Ed25519JoseCredentialProofService(
            KEY_ID, URI_PROVIDER, incompatible.getPrivate(), incompatible.getPublic(), new ObjectMapper());

        assertThatThrownBy(() -> incompatibleService.sign(validDocument()))
            .isInstanceOfSatisfying(CredentialProofService.ProofException.class, exception -> {
                assertThat(exception.failureCode()).isEqualTo(
                    CredentialProofService.FailureCode.CREDENTIAL_PROOF_GENERATION_FAILED);
                assertThat(exception.getMessage()).isEqualTo("Credential proof generation failed");
                assertThat(exception.getMessage()).doesNotContain(KEY_ID, validPayload);
        });
    }

    @Test
    void refusesToSignCredentialDocumentOutsideTheWhitelist() {
        CredentialDocument arbitrary = new CredentialDocument(
            "{\"arbitrary\":\"canonical-json\"}");

        assertThatThrownBy(() -> service.sign(arbitrary))
            .isInstanceOfSatisfying(CredentialProofService.ProofException.class, exception -> {
                assertThat(exception.failureCode()).isEqualTo(
                    CredentialProofService.FailureCode.CREDENTIAL_PROOF_GENERATION_FAILED);
                assertThat(exception.getMessage()).isEqualTo("Credential proof generation failed");
                assertThat(exception.getMessage()).doesNotContain(arbitrary.canonicalJson());
            });
    }

    @Test
    void rejectsWrongOrNonCanonicalProtectedHeader() throws Exception {
        for (String header : List.of(
            "{\"alg\":\"RS256\",\"cty\":\"vc\",\"kid\":\"" + KEY_ID + "\",\"typ\":\"vc+jwt\"}",
            "{\"alg\":\"EdDSA\",\"cty\":\"json\",\"kid\":\"" + KEY_ID + "\",\"typ\":\"vc+jwt\"}",
            "{\"alg\":\"EdDSA\",\"cty\":\"vc\",\"kid\":\"wrong\",\"typ\":\"vc+jwt\"}",
            "{\"alg\":\"EdDSA\",\"cty\":\"vc\",\"kid\":\"" + KEY_ID + "\",\"typ\":\"JWT\"}",
            "{\"alg\":\"EdDSA\",\"alg\":\"EdDSA\",\"cty\":\"vc\",\"kid\":\"" + KEY_ID + "\",\"typ\":\"vc+jwt\"}",
            "{\"alg\":\"EdDSA\",\"cty\":\"vc\",\"kid\":\"" + KEY_ID + "\",\"typ\":\"vc+jwt\",\"x\":1}"
        )) {
            assertInvalid(signRaw(header, validPayload, keyPair.getPrivate()));
        }
    }

    @Test
    void rejectsSignedCredentialWithUnknownOrMissingRootAndSubjectClaims() throws Exception {
        assertInvalidPayload(validPayload.replace(
            "\"credentialSubject\":", "\"unexpected\":true,\"credentialSubject\":"));
        assertInvalidPayload(validPayload.replace(
            "\"issuer\":\"" + ISSUER + "\",", ""));
        assertInvalidPayload(validPayload.replace(
            "\"completionId\":", "\"unexpected\":true,\"completionId\":"));
        assertInvalidPayload(validPayload.replace(
            "\"courseId\":\"60000000-0000-0000-0000-000000000006\",", ""));
    }

    @Test
    void rejectsArbitraryCanonicalJsonEvenWhenSignedByTheExpectedKey() throws Exception {
        assertInvalidPayload("{\"arbitrary\":\"canonical-json\"}");
    }

    @Test
    void rejectsSignedCredentialWithWrongContextTypeUrnsOrRfc3339Times() throws Exception {
        assertInvalidPayload(validPayload.replace(
            "https://www.w3.org/ns/credentials/v2", "https://example.invalid/credentials"));
        assertInvalidPayload(validPayload.replace(
            "LifelongEducationCompletionCredential", "OtherCredential"));
        assertInvalidPayload(validPayload.replace(
            "urn:uuid:10000000-0000-0000-0000-000000000001", "did:example:credential"));
        assertInvalidPayload(validPayload.replace(
            ISSUER, "did:example:issuer"));
        assertInvalidPayload(validPayload.replace(
            "urn:dabaeum:user:30000000-0000-0000-0000-000000000003", "did:example:subject"));
        assertInvalidPayload(validPayload.replace(
            "40000000-0000-0000-0000-000000000004", "not-a-completion-uuid"));
        assertInvalidPayload(validPayload.replace("2026-08-05T01:02:03Z", "2026-08-05"));
        assertInvalidPayload(validPayload.replace("2026-08-04T10:20:30Z", "not-a-time"));
        assertInvalidPayload(validPayload.replace("2027-08-05T01:02:03Z", "2025-08-05T01:02:03Z"));
    }

    @Test
    void rejectsSignedCredentialWithWrongNumericTypesOrScale() throws Exception {
        assertInvalidPayload(validPayload.replace("\"attendanceRate\":90.00", "\"attendanceRate\":90.0"));
        assertInvalidPayload(validPayload.replace("\"attendanceRate\":90.00", "\"attendanceRate\":-1.00"));
        assertInvalidPayload(validPayload.replace("\"attendanceRate\":90.00", "\"attendanceRate\":100.01"));
        assertInvalidPayload(validPayload.replace("\"completedMinutes\":1200", "\"completedMinutes\":1200.0"));
        assertInvalidPayload(validPayload.replace("\"completedMinutes\":1200", "\"completedMinutes\":-1"));
        assertInvalidPayload(validPayload.replace("\"creditValue\":3.00", "\"creditValue\":3.0"));
        assertInvalidPayload(validPayload.replace("\"creditValue\":3.00", "\"creditValue\":-1.00"));
    }

    @Test
    void envelopeDeserializationRejectsUnknownField() {
        String json = "{\"mediaType\":\"application/vc+jwt\","
            + "\"compactJws\":\"header.payload.signature\",\"proof\":\"forbidden\"}";

        assertThatThrownBy(() -> new ObjectMapper().readValue(json, SignedCredentialEnvelope.class))
            .satisfies(exception -> assertThat(exception.getMessage())
                .doesNotContain("header.payload.signature"));
    }

    private SignedCredentialEnvelope signRaw(String header, String payload, PrivateKey privateKey)
        throws Exception {
        String encodedHeader = encode(header);
        String encodedPayload = encode(payload);
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update((encodedHeader + "." + encodedPayload).getBytes(StandardCharsets.US_ASCII));
        return new SignedCredentialEnvelope("application/vc+jwt",
            encodedHeader + "." + encodedPayload + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign()));
    }

    private void assertInvalid(SignedCredentialEnvelope envelope) {
        assertThatThrownBy(() -> service.verify(envelope))
            .isInstanceOfSatisfying(CredentialProofService.ProofException.class, exception -> {
                assertThat(exception.failureCode()).isEqualTo(
                    CredentialProofService.FailureCode.CREDENTIAL_PROOF_INVALID);
                assertThat(exception.getMessage()).isEqualTo("Credential proof is invalid");
                assertThat(exception.getMessage()).doesNotContain(
                    KEY_ID, validPayload, envelope.compactJws());
            });
    }

    private void assertInvalidPayload(String payload) throws Exception {
        SignedCredentialEnvelope envelope = signRaw(EXACT_HEADER, payload, keyPair.getPrivate());
        assertThatThrownBy(() -> service.verify(envelope))
            .isInstanceOfSatisfying(CredentialProofService.ProofException.class, exception -> {
                assertThat(exception.failureCode()).isEqualTo(
                    CredentialProofService.FailureCode.CREDENTIAL_PROOF_INVALID);
                assertThat(exception.getMessage()).isEqualTo("Credential proof is invalid");
                assertThat(exception.getMessage()).doesNotContain(
                    KEY_ID, payload, envelope.compactJws());
            });
    }

    @Test
    void signedCredentialCarriesTheBitstringStatusListEntry() {
        String payload = validDocument().canonicalJson();

        assertThat(payload).contains("\"credentialStatus\":{"
            + "\"id\":\"" + LIST_URL + "#94567\","
            + "\"type\":\"BitstringStatusListEntry\","
            + "\"statusPurpose\":\"revocation\","
            + "\"statusListIndex\":\"94567\","
            + "\"statusListCredential\":\"" + LIST_URL + "\"}");
        assertThat(service.verify(service.sign(validDocument())).canonicalJson())
            .isEqualTo(payload);
    }

    @Test
    void stillVerifiesTheCustomStatusTypeIssuedBeforeTheBitstringTransition() throws Exception {
        String bitstringStatus = validPayload.substring(validPayload.indexOf("\"credentialStatus\""));
        String previousFormat = validPayload.replace(
            bitstringStatus.substring(0, bitstringStatus.length() - 1), LEGACY_STATUS);

        assertThat(service.verify(signRaw(EXACT_HEADER, previousFormat, keyPair.getPrivate()))
            .canonicalJson()).isEqualTo(previousFormat);
    }

    @Test
    void rejectsTamperedOrInconsistentBitstringStatusReferences() throws Exception {
        // id, statusListIndex, statusListCredential 이 서로 맞지 않으면 거부한다.
        assertInvalidPayload(validPayload.replace(LIST_URL + "#94567", LIST_URL + "#94568"));
        assertInvalidPayload(validPayload.replace(
            "\"statusListIndex\":\"94567\"", "\"statusListIndex\":94567"));
        assertInvalidPayload(validPayload.replace(
            "\"statusPurpose\":\"revocation\"", "\"statusPurpose\":\"suspension\""));
        assertInvalidPayload(validPayload.replace(
            "\"type\":\"BitstringStatusListEntry\"", "\"type\":\"StatusList2021Entry\""));
        assertInvalidPayload(validPayload.replace(
            LIST_URL + "\"}}", "https://evil.example/status-lists/" + LIST_ID + "\"}}"));
    }

    @Test
    void signsAndVerifiesTheStatusListCredentialWithTheIssuerKey() {
        CredentialDocument statusList = statusListDocument(
            "uH4sIAAAAAAAC_-3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA");

        SignedCredentialEnvelope signed = service.signStatusList(statusList);

        assertThat(decode(signed.compactJws().split("\\.", -1)[0])).isEqualTo(EXACT_HEADER);
        assertThat(service.verifyStatusList(signed)).isEqualTo(statusList);
    }

    @Test
    void refusesStatusListDocumentsCarryingAnythingBeyondTheBits() {
        String valid = statusListDocument("uAAAA").canonicalJson();

        for (String payload : List.of(
            valid.replace("\"type\":\"BitstringStatusList\"", "\"type\":\"RevocationList2020\""),
            valid.replace("\"encodedList\":\"uAAAA\"", "\"encodedList\":\"AAAA\""),
            valid.replace("\"statusPurpose\":\"revocation\"", "\"statusPurpose\":\"message\""),
            valid.replace("\"encodedList\":", "\"userName\":\"leaked\",\"encodedList\":"),
            valid.replace(LIST_URL + "#list", LIST_URL + "#other")
        )) {
            assertThatThrownBy(() -> service.signStatusList(new CredentialDocument(payload)))
                .isInstanceOfSatisfying(CredentialProofService.ProofException.class, exception ->
                    assertThat(exception.failureCode()).isEqualTo(
                        CredentialProofService.FailureCode.CREDENTIAL_PROOF_GENERATION_FAILED));
        }
        assertThatThrownBy(() -> service.verifyStatusList(service.sign(validDocument())))
            .isInstanceOf(CredentialProofService.ProofException.class);
    }

    private CredentialDocument statusListDocument(String encodedList) {
        return new CredentialStatusListDocumentFactory(
            new ObjectMapper(), new DabaeumUrnCredentialIdentifierProvider(URI_PROVIDER)).create(
                LIST_ID, UUID.fromString("20000000-0000-0000-0000-000000000002"),
                Instant.parse("2026-09-03T01:02:03Z"), encodedList);
    }

    private CredentialDocument validDocument() {
        return new CredentialDocumentFactory(
            new ObjectMapper(), new DabaeumUrnCredentialIdentifierProvider(URI_PROVIDER)).create(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                STATUS_ENTRY,
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                UUID.fromString("40000000-0000-0000-0000-000000000004"),
                UUID.fromString("50000000-0000-0000-0000-000000000005"),
                UUID.fromString("60000000-0000-0000-0000-000000000006"),
                Instant.parse("2026-08-05T01:02:03Z"),
                Instant.parse("2027-08-05T01:02:03Z"),
                Instant.parse("2026-08-04T10:20:30Z"),
                new BigDecimal("90.00"), 1200, new BigDecimal("3.00"));
    }

    private String legacyPayload() {
        return "{\"@context\":[\"https://www.w3.org/ns/credentials/v2\"],"
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
    }

    private String mutate(String segment) {
        char replacement = segment.charAt(0) == 'A' ? 'B' : 'A';
        return replacement + segment.substring(1);
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
