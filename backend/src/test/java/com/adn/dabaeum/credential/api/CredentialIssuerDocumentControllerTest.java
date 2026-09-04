package com.adn.dabaeum.credential.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.credential.application.CredentialIssuerDocumentService;
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.application.VcPublicResourceService;
import com.adn.dabaeum.credential.application.PublicCredentialStatusService;
import com.adn.dabaeum.credential.infrastructure.crypto.Ed25519PublicJwkFactory;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
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

    @BeforeEach
    void setUp() throws Exception {
        when(uriProvider.issuerUrl(INSTITUTION_ID)).thenReturn(ISSUER);
        when(uriProvider.keyId(INSTITUTION_ID)).thenReturn(ISSUER + "#development-1");
        when(publicKey.getEncoded()).thenReturn(
            KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
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

    private Institution institution() {
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        return new Institution(INSTITUTION_ID, "ADN-EDU-001", "이노텍평생교육원",
            null, null, null, null, null, InstitutionStatus.INACTIVE, now, now, null);
    }
}
