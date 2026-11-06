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
import com.adn.dabaeum.credential.domain.CredentialProofService;
import com.adn.dabaeum.credential.domain.CredentialSigningInput;
import com.adn.dabaeum.credential.domain.CredentialStatusList;
import com.adn.dabaeum.credential.domain.CredentialStatusListEntry;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.domain.StatusListSigningInputFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

/** 설계서 §9.1: Status List signing input 은 파일 키 Status List 서명과 같은 규칙이고 kid 만 Status List 버전 조각이다. */
class JoseStatusListSigningInputTest {

    private static final String LEGACY_KEY_ID = "urn:dabaeum:credential-key:development-1";
    private static final UUID INSTITUTION = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OTHER = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final CredentialUriProvider URIS = new DefaultCredentialUriProvider(
        new VcProperties("https://vc.example.test", "v1", "development-1"));

    private final Ed25519JoseCredentialProofService keyless =
        Ed25519JoseCredentialProofService.signingInputOnly(LEGACY_KEY_ID, URIS, new ObjectMapper());

    @Test
    void 파일_키_Status_List_서명과_같은_payload_조각이고_kid_만_Status_List_버전_조각이다() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Ed25519JoseCredentialProofService fileKey = new Ed25519JoseCredentialProofService(
            LEGACY_KEY_ID, URIS, keys.getPrivate(), keys.getPublic(), new ObjectMapper());
        CredentialDocument document = statusListDocument(INSTITUTION);
        String[] signed = fileKey.signStatusList(document).compactJws().split("\\.", -1);

        CredentialSigningInput input = keyless.statusListSigningInput(
            document, INSTITUTION, URIS.keyId(INSTITUTION, "status-v1"));

        assertThat(input.encodedPayload()).isEqualTo(signed[1]);
        assertThat(decode(input.encodedHeader())).isEqualTo("{\"alg\":\"EdDSA\",\"cty\":\"vc\",\"kid\":\""
            + URIS.issuerUrl(INSTITUTION) + "#status-v1\",\"typ\":\"vc+jwt\"}");
    }

    @Test
    void 다른_기관의_kid_나_issuer_와_수료증_문서와_비정규형은_거부한다() {
        CredentialDocument document = statusListDocument(INSTITUTION);
        String kid = URIS.keyId(INSTITUTION, "status-v1");

        assertGenerationFailed(() -> keyless.statusListSigningInput(
            document, INSTITUTION, URIS.keyId(OTHER, "status-v1")));
        assertGenerationFailed(() -> keyless.statusListSigningInput(
            document, OTHER, URIS.keyId(OTHER, "status-v1")));
        assertGenerationFailed(() -> keyless.statusListSigningInput(
            document, INSTITUTION, URIS.issuerUrl(INSTITUTION) + "#"));
        assertGenerationFailed(() -> keyless.statusListSigningInput(
            new CredentialDocument(document.canonicalJson().replace(",\"type\":", ", \"type\":")),
            INSTITUTION, kid));
        assertGenerationFailed(() -> keyless.statusListSigningInput(credentialDocument(), INSTITUTION, kid));
    }

    @Test
    void 설정은_키_파일_없이_Status_List_signing_input_생성기를_만든다() {
        new ApplicationContextRunner()
            .withUserConfiguration(CredentialProofConfiguration.class)
            .run(context -> assertThat(context).hasSingleBean(StatusListSigningInputFactory.class));
    }

    private static CredentialDocument statusListDocument(UUID institutionId) {
        return new CredentialStatusListDocumentFactory(
            new ObjectMapper(), new DabaeumUrnCredentialIdentifierProvider(URIS)).create(
                UUID.fromString("70000000-0000-0000-0000-000000000007"), institutionId,
                Instant.parse("2026-09-03T01:02:03Z"),
                new BitstringStatusListEncoder().encode(CredentialStatusList.MINIMUM_CAPACITY, List.of(12)));
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

    private static String decode(String segment) {
        return new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
    }

    private static void assertGenerationFailed(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CredentialProofService.ProofException.class,
            exception -> assertThat(exception.failureCode())
                .isEqualTo(CredentialProofService.FailureCode.CREDENTIAL_PROOF_GENERATION_FAILED));
    }
}
