package com.adn.dabaeum.credential.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.credential.application.CredentialIssuerDocumentService;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.application.VcPublicResourceService;
import com.adn.dabaeum.credential.application.PublicCredentialStatusService;
import com.adn.dabaeum.credential.domain.InstitutionSignKey;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyRepository;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersion;
import com.adn.dabaeum.credential.domain.InstitutionSignKeyVersionRepository;
import com.adn.dabaeum.credential.domain.SignKeyPurpose;
import com.adn.dabaeum.credential.infrastructure.crypto.Ed25519PublicJwkFactory;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VcPublicResourceController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=vc-issuer-test-token")
@Import({
    VcPublicResourceService.class,
    CredentialIssuerDocumentService.class,
    Ed25519PublicJwkFactory.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CredentialIssuerDocumentControllerTest {

    private static final UUID INSTITUTION_ID = UUID.fromString(
        "4248ab4f-27a1-4bfd-a09a-f863e2ffaeb1");
    private static final String ISSUER =
        "https://vc.example.test/api/v1/vc/issuers/" + INSTITUTION_ID;

    @Autowired MockMvc mockMvc;
    @MockitoBean CredentialUriProvider uriProvider;
    @MockitoBean InstitutionRepository institutions;
    @MockitoBean PublicKey publicKey;
    @MockitoBean PublicCredentialStatusService publicCredentialStatusService;
    @MockitoBean InstitutionSignKeyRepository signKeys;
    @MockitoBean InstitutionSignKeyVersionRepository signKeyVersions;

    @BeforeEach
    void setUp() throws Exception {
        when(uriProvider.issuerUrl(INSTITUTION_ID)).thenReturn(ISSUER);
        when(uriProvider.keyId(INSTITUTION_ID)).thenReturn(ISSUER + "#development-1");
        when(publicKey.getEncoded()).thenReturn(
            KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
        when(uriProvider.keyId(eq(INSTITUTION_ID), anyString()))
            .thenAnswer(invocation -> ISSUER + "#" + invocation.getArgument(1));
    }

    @Test
    void inactiveInstitutionStillPublishesControlledIdentifierAndPublicJwk() throws Exception {
        when(institutions.findById(INSTITUTION_ID)).thenReturn(Optional.of(institution()));

        mockMvc.perform(get("/api/v1/vc/issuers/{institutionId}", INSTITUTION_ID))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/ld+json"))
            .andExpect(jsonPath("$.['@context']").value("https://www.w3.org/ns/cid/v1"))
            .andExpect(jsonPath("$.id").value(ISSUER))
            .andExpect(jsonPath("$.verificationMethod[0].id")
                .value(ISSUER + "#development-1"))
            .andExpect(jsonPath("$.verificationMethod[0].type").value("JsonWebKey"))
            .andExpect(jsonPath("$.verificationMethod[0].controller").value(ISSUER))
            .andExpect(jsonPath("$.verificationMethod[0].publicKeyJwk.kty").value("OKP"))
            .andExpect(jsonPath("$.verificationMethod[0].publicKeyJwk.crv").value("Ed25519"))
            .andExpect(jsonPath("$.verificationMethod[0].publicKeyJwk.x").isNotEmpty())
            .andExpect(jsonPath("$.verificationMethod[0].publicKeyJwk.privateKey").doesNotExist())
            .andExpect(jsonPath("$.assertionMethod[0]").value(ISSUER + "#development-1"));
    }

    @Test
    void missingInstitutionReturnsNotFoundWithoutRevealingKeyMaterial() throws Exception {
        when(institutions.findById(INSTITUTION_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/vc/issuers/{institutionId}", INSTITUTION_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INSTITUTION_NOT_FOUND"))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("PRIVATE KEY"))));
    }

    @Test
    void malformedConfiguredPublicKeyReturnsServiceUnavailableSafely() throws Exception {
        when(institutions.findById(INSTITUTION_ID)).thenReturn(Optional.of(institution()));
        when(publicKey.getEncoded()).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/v1/vc/issuers/{institutionId}", INSTITUTION_ID))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.message").value("Credential issuer key is unavailable"));
    }

    @Test
    void publishesEveryNonCompromisedVersionOfBothKeysAndDropsTheFileKeyOnceTheLegacyRowExists()
        throws Exception {
        when(institutions.findById(INSTITUTION_ID)).thenReturn(Optional.of(institution()));
        Instant now = Instant.parse("2026-09-11T00:00:00Z");
        InstitutionSignKey credential = InstitutionSignKey.provisioning(UUID.randomUUID(),
            INSTITUTION_ID, SignKeyPurpose.CREDENTIAL, 1, now).activate(1, now)
            .rotated(2, now).rotated(3, now);
        InstitutionSignKey statusList = InstitutionSignKey.provisioning(UUID.randomUUID(),
            INSTITUTION_ID, SignKeyPurpose.STATUS_LIST, 1, now).activate(1, now);
        String v3Jwk = storedJwk();
        String legacyJwk = storedJwk();
        when(signKeys.findByInstitutionId(INSTITUTION_ID)).thenReturn(List.of(statusList, credential));
        when(signKeyVersions.findByInstitutionId(INSTITUTION_ID)).thenReturn(List.of(
            version(statusList, 1, storedJwk(), now),
            InstitutionSignKeyVersion.legacy(UUID.randomUUID(), credential.id(), INSTITUTION_ID, legacyJwk, now),
            version(credential, 1, storedJwk(), now).retire(now),
            version(credential, 2, storedJwk(), now).compromise(now),
            version(credential, 3, v3Jwk, now)));

        mockMvc.perform(get("/api/v1/vc/issuers/{institutionId}", INSTITUTION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationMethod.length()").value(4))
            .andExpect(jsonPath("$.verificationMethod[0].id").value(ISSUER + "#v3"))
            .andExpect(jsonPath("$.verificationMethod[0].publicKeyJwk.kid").value(ISSUER + "#v3"))
            .andExpect(jsonPath("$.verificationMethod[0].publicKeyJwk.x").value(x(v3Jwk)))
            .andExpect(jsonPath("$.verificationMethod[1].id").value(ISSUER + "#v1"))
            .andExpect(jsonPath("$.verificationMethod[2].id").value(ISSUER + "#development-1"))
            .andExpect(jsonPath("$.verificationMethod[2].publicKeyJwk.x").value(x(legacyJwk)))
            .andExpect(jsonPath("$.verificationMethod[3].id").value(ISSUER + "#status-v1"))
            .andExpect(jsonPath("$.verificationMethod[3].controller").value(ISSUER))
            .andExpect(jsonPath("$.assertionMethod.length()").value(4))
            .andExpect(jsonPath("$.assertionMethod[0]").value(ISSUER + "#v3"))
            .andExpect(jsonPath("$.assertionMethod[3]").value(ISSUER + "#status-v1"));
    }

    @Test
    void aCompromisedLegacyRowIsNotRevivedByTheConfiguredFileKey() throws Exception {
        when(institutions.findById(INSTITUTION_ID)).thenReturn(Optional.of(institution()));
        Instant now = Instant.parse("2026-09-11T00:00:00Z");
        InstitutionSignKey credential = InstitutionSignKey.provisioning(UUID.randomUUID(),
            INSTITUTION_ID, SignKeyPurpose.CREDENTIAL, 1, now).activate(1, now);
        when(signKeys.findByInstitutionId(INSTITUTION_ID)).thenReturn(List.of(credential));
        when(signKeyVersions.findByInstitutionId(INSTITUTION_ID)).thenReturn(List.of(
            InstitutionSignKeyVersion.legacy(UUID.randomUUID(), credential.id(), INSTITUTION_ID,
                storedJwk(), now).compromise(now),
            version(credential, 1, storedJwk(), now)));

        mockMvc.perform(get("/api/v1/vc/issuers/{institutionId}", INSTITUTION_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationMethod.length()").value(1))
            .andExpect(jsonPath("$.verificationMethod[0].id").value(ISSUER + "#v1"));
    }

    @Test
    void aVersionWithoutItsMasterOrWithACorruptStoredJwkIsServiceUnavailable() throws Exception {
        when(institutions.findById(INSTITUTION_ID)).thenReturn(Optional.of(institution()));
        Instant now = Instant.parse("2026-09-11T00:00:00Z");
        InstitutionSignKey credential = InstitutionSignKey.provisioning(UUID.randomUUID(),
            INSTITUTION_ID, SignKeyPurpose.CREDENTIAL, 1, now).activate(1, now);
        when(signKeyVersions.findByInstitutionId(INSTITUTION_ID))
            .thenReturn(List.of(version(credential, 1, storedJwk(), now)));

        mockMvc.perform(get("/api/v1/vc/issuers/{institutionId}", INSTITUTION_ID))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value("Credential issuer key is unavailable"));

        when(signKeys.findByInstitutionId(INSTITUTION_ID)).thenReturn(List.of(credential));
        when(signKeyVersions.findByInstitutionId(INSTITUTION_ID)).thenReturn(List.of(
            version(credential, 1, "{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"alg\":\"EdDSA\",\"use\":\"sig\",\"x\":\"AA\"}", now)));

        mockMvc.perform(get("/api/v1/vc/issuers/{institutionId}", INSTITUTION_ID))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("\"x\""))));
    }

    private static InstitutionSignKeyVersion version(
        InstitutionSignKey master, int keyVersion, String storedJwk, Instant now
    ) {
        return InstitutionSignKeyVersion.active(UUID.randomUUID(), master.id(), INSTITUTION_ID,
            master.purpose(), master.generation(), keyVersion, storedJwk, now, null, now);
    }

    /** 버전 행에 보존하는 모양 — kid 없는 다섯 필드, jsonb 처럼 순서를 바꿔 둔다. */
    private static String storedJwk() throws Exception {
        Map<String, String> jwk = new Ed25519PublicJwkFactory().create("ignored",
            KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic());
        return "{\"x\": \"" + jwk.get("x") + "\", \"alg\": \"EdDSA\", \"crv\": \"Ed25519\", "
            + "\"kty\": \"OKP\", \"use\": \"sig\"}";
    }

    private static String x(String storedJwk) {
        int start = storedJwk.indexOf("\"x\": \"") + 6;
        return storedJwk.substring(start, storedJwk.indexOf('"', start));
    }

    private Institution institution() {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        return new Institution(INSTITUTION_ID, "ADN-EDU-001", "이노텍평생교육원",
            null, null, null, null, null, InstitutionStatus.INACTIVE, now, now, null);
    }
}
