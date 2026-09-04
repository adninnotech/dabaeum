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
import com.adn.dabaeum.credential.domain.CredentialUriProvider;
import com.adn.dabaeum.credential.application.CredentialIssuerDocumentService;
import com.adn.dabaeum.credential.application.VcPublicResourceService;
import com.adn.dabaeum.credential.application.PublicCredentialStatusService;
import com.adn.dabaeum.credential.application.CredentialStatusListService;
import com.adn.dabaeum.credential.domain.SignedCredentialEnvelope;
import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import java.time.Instant;
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
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=vc-public-test-token")
@Import({
    VcPublicResourceService.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class VcPublicResourceControllerTest {

    private static final String BASE = "https://vc.example.test/api/v1/vc";

    @Autowired MockMvc mockMvc;
    @MockitoBean CredentialUriProvider uriProvider;
    @MockitoBean CredentialIssuerDocumentService issuerDocumentService;
    @MockitoBean PublicCredentialStatusService publicCredentialStatusService;
    @MockitoBean CredentialStatusListService credentialStatusListService;

    @BeforeEach
    void configureUris() {
        when(uriProvider.contextUrl()).thenReturn(
            BASE + "/contexts/lifelong-education/v1");
        when(uriProvider.vocabularyUrl()).thenReturn(
            BASE + "/vocabulary/lifelong-education/v1");
    }

    @Test
    void contextIsPublicVersionedAndDefinesEveryCustomTerm() throws Exception {
        String vocabulary = BASE + "/vocabulary/lifelong-education/v1#";

        mockMvc.perform(get("/api/v1/vc/contexts/lifelong-education/v1"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/ld+json"))
            .andExpect(jsonPath("$.['@context'].['@protected']").value(true))
            .andExpect(jsonPath("$.['@context'].LifelongEducationCompletionCredential")
                .value(vocabulary + "LifelongEducationCompletionCredential"))
            .andExpect(jsonPath("$.['@context'].DabaeumCredentialStatus")
                .value(vocabulary + "DabaeumCredentialStatus"))
            .andExpect(jsonPath("$.['@context'].completionId").value(vocabulary + "completionId"))
            .andExpect(jsonPath("$.['@context'].enrollmentId").value(vocabulary + "enrollmentId"))
            .andExpect(jsonPath("$.['@context'].courseId").value(vocabulary + "courseId"))
            .andExpect(jsonPath("$.['@context'].completedAt.['@type']")
                .value("http://www.w3.org/2001/XMLSchema#dateTime"))
            .andExpect(jsonPath("$.['@context'].attendanceRate.['@type']")
                .value("http://www.w3.org/2001/XMLSchema#decimal"))
            .andExpect(jsonPath("$.['@context'].completedMinutes.['@type']")
                .value("http://www.w3.org/2001/XMLSchema#integer"))
            .andExpect(jsonPath("$.['@context'].creditValue.['@type']")
                .value("http://www.w3.org/2001/XMLSchema#decimal"));
    }

    @Test
    void vocabularyIsPublicAndDocumentsTermUrlsInKorean() throws Exception {
        mockMvc.perform(get("/api/v1/vc/vocabulary/lifelong-education/v1"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.id").value(
                BASE + "/vocabulary/lifelong-education/v1"))
            .andExpect(jsonPath("$.version").value("v1"))
            .andExpect(jsonPath("$.terms.length()").value(9))
            .andExpect(jsonPath("$.terms[0].term")
                .value("LifelongEducationCompletionCredential"))
            .andExpect(jsonPath("$.terms[0].id").value(
                BASE + "/vocabulary/lifelong-education/v1#LifelongEducationCompletionCredential"))
            .andExpect(jsonPath("$.terms[0].description").value("평생교육 과정 이수 증명 Credential"))
            .andExpect(jsonPath("$.terms[0].valueFormat").value("type"));
    }

    @Test
    void statusIsPublicNoStoreAndExposesOnlyWhitelistedFields() throws Exception {
        when(publicCredentialStatusService.check(
            org.mockito.ArgumentMatchers.eq("CERT-VERIFY-001"),
            org.mockito.ArgumentMatchers.any())).thenReturn(
                new PublicCredentialStatusService.Status(
                    "CERT-VERIFY-001", CredentialVerificationResult.VALID,
                    Instant.parse("2026-08-10T00:00:00Z")));

        mockMvc.perform(get("/api/v1/vc/status/CERT-VERIFY-001"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.credentialNo").value("CERT-VERIFY-001"))
            .andExpect(jsonPath("$.status").value("VALID"))
            .andExpect(jsonPath("$.checkedAt").value("2026-08-10T00:00:00Z"))
            .andExpect(jsonPath("$.credentialId").doesNotExist())
            .andExpect(jsonPath("$.credentialHash").doesNotExist())
            .andExpect(jsonPath("$.transactionId").doesNotExist());
    }

    @Test
    void statusListIsPublicSignedAndCacheableSoVerifiersHideWhichCredentialTheyCheck()
        throws Exception {
        java.util.UUID listId = java.util.UUID.fromString("70000000-0000-0000-0000-000000000007");
        when(credentialStatusListService.document(listId)).thenReturn(
            new SignedCredentialEnvelope("application/vc+jwt", "header.payload.signature"));

        mockMvc.perform(get("/api/v1/vc/status-lists/" + listId))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/vc+jwt"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Cache-Control", "max-age=60, public"))
            .andExpect(content().string("header.payload.signature"));
    }
}
