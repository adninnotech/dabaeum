package com.adn.dabaeum.credential.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.credential.application.CredentialDocumentFactory;
import com.adn.dabaeum.credential.application.DabaeumUrnCredentialIdentifierProvider;
import com.adn.dabaeum.credential.application.DefaultCredentialUriProvider;
import com.adn.dabaeum.credential.config.VcProperties;
import com.adn.dabaeum.credential.domain.CredentialDocument;
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialSigningInput;
import com.adn.dabaeum.credential.domain.CredentialSigningInputFactory;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

/** 설계서 §7.2: Transit 서명 입력은 파일 키 서명과 같은 헤더·payload 규칙으로 만든다. kid 만 버전 행 조각이다. */
class JoseCredentialSigningInputTest {

    private static final String LEGACY_KEY_ID = "urn:dabaeum:credential-key:development-1";
    private static final UUID INSTITUTION = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OTHER = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final CredentialUriProvider URI_PROVIDER = new DefaultCredentialUriProvider(
        new VcProperties("https://vc.example.test", "v1", "development-1"));
    private static final CredentialDocumentFactory DOCUMENTS = new CredentialDocumentFactory(
        new ObjectMapper(), new DabaeumUrnCredentialIdentifierProvider(URI_PROVIDER));

    private final Ed25519JoseCredentialProofService inputOnly =
        Ed25519JoseCredentialProofService.signingInputOnly(LEGACY_KEY_ID, URI_PROVIDER, new ObjectMapper());

    @Test
    void 파일_키_서명과_같은_헤더와_payload_조각을_만든다() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Ed25519JoseCredentialProofService fileKey = new Ed25519JoseCredentialProofService(
            LEGACY_KEY_ID, URI_PROVIDER, keys.getPrivate(), keys.getPublic(), new ObjectMapper());
        CredentialDocument document = document(INSTITUTION);

        String[] signed = fileKey.sign(document).compactJws().split("\\.", -1);
        CredentialSigningInput input = inputOnly.credentialSigningInput(
            document, INSTITUTION, URI_PROVIDER.keyId(INSTITUTION));

        assertThat(input.encodedHeader()).isEqualTo(signed[0]);
        assertThat(input.encodedPayload()).isEqualTo(signed[1]);
    }

    @Test
    void kid_는_버전_행_조각으로_바뀌고_payload_는_그대로다() {
        CredentialDocument document = document(INSTITUTION);

        CredentialSigningInput v3 = inputOnly.credentialSigningInput(
            document, INSTITUTION, URI_PROVIDER.keyId(INSTITUTION, "v3"));
        CredentialSigningInput v4 = inputOnly.credentialSigningInput(
            document, INSTITUTION, URI_PROVIDER.keyId(INSTITUTION, "v4"));

        assertThat(decode(v3.encodedHeader())).isEqualTo("{\"alg\":\"EdDSA\",\"cty\":\"vc\",\"kid\":"
            + "\"https://vc.example.test/api/v1/vc/issuers/20000000-0000-0000-0000-000000000002#v3\","
            + "\"typ\":\"vc+jwt\"}");
        assertThat(v4.encodedPayload()).isEqualTo(v3.encodedPayload());
        assertThat(v4.document()).isEqualTo(document);
    }

    @Test
    void 다른_기관의_kid_나_payload_와_빈_조각은_거부한다() {
        CredentialDocument document = document(INSTITUTION);

        assertGenerationFailed(() -> inputOnly.credentialSigningInput(
            document, INSTITUTION, URI_PROVIDER.keyId(OTHER, "v1")));
        assertGenerationFailed(() -> inputOnly.credentialSigningInput(
            document, OTHER, URI_PROVIDER.keyId(OTHER, "v1")));
        assertGenerationFailed(() -> inputOnly.credentialSigningInput(
            document, INSTITUTION, URI_PROVIDER.issuerUrl(INSTITUTION) + "#"));
    }

    @Test
    void 정규_직렬화가_아닌_payload_는_거부한다() {
        String spaced = document(INSTITUTION).canonicalJson().replace(",\"id\":", ", \"id\":");

        assertGenerationFailed(() -> inputOnly.credentialSigningInput(
            new CredentialDocument(spaced), INSTITUTION, URI_PROVIDER.keyId(INSTITUTION, "v1")));
    }

    @Test
    void 키_없는_인스턴스는_서명하지_않는다() {
        assertGenerationFailed(() -> inputOnly.sign(document(INSTITUTION)));
    }

    @Test
    void 설정은_키_파일_없이_서명_입력_생성기를_만든다() {
        new ApplicationContextRunner()
            .withUserConfiguration(CredentialProofConfiguration.class)
            .run(context -> assertThat(context).hasSingleBean(CredentialSigningInputFactory.class));
    }

    private static CredentialDocument document(UUID institutionId) {
        return DOCUMENTS.create(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            new CredentialStatusListEntry(UUID.fromString("70000000-0000-0000-0000-000000000007"), 94_567),
            institutionId,
            UUID.fromString("40000000-0000-0000-0000-000000000004"),
            UUID.fromString("50000000-0000-0000-0000-000000000005"),
            UUID.fromString("60000000-0000-0000-0000-000000000006"),
            UUID.fromString("80000000-0000-0000-0000-000000000008"),
            Instant.parse("2026-09-01T00:00:00Z"), null, Instant.parse("2026-08-31T00:00:00Z"),
            new BigDecimal("95.50"), 1200, null);
    }

    private static String decode(String segment) {
        return new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
    }

    private static void assertGenerationFailed(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CredentialProofService.ProofException.class,
            exception -> assertThat(exception.failureCode())
                .isEqualTo(CredentialProofService.FailureCode.CREDENTIAL_PROOF_GENERATION_FAILED));
    }
}
