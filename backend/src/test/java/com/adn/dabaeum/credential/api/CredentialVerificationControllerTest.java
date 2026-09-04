package com.adn.dabaeum.credential.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.credential.application.CredentialApplicationService;
import com.adn.dabaeum.credential.application.CredentialDocumentDownloadService;
import com.adn.dabaeum.credential.application.CredentialVerificationPage;
import com.adn.dabaeum.credential.application.CredentialVerificationService;
import com.adn.dabaeum.credential.domain.CredentialVerification;
import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CredentialController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=credential-task9-token")
@Import({CredentialApiMapper.class, ApiExceptionHandler.class, ClockConfiguration.class,
    SecurityConfiguration.class, DevBearerSecurityConfiguration.class, JacksonConfiguration.class,
    RequestIdFilter.class, AuthorizationPolicy.class})
class CredentialVerificationControllerTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID CREDENTIAL_ID = UUID.fromString(
        "70000000-0000-0000-0000-000000000007");
    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "50000000-0000-0000-0000-000000000005");

    @Autowired MockMvc mockMvc;
    @MockitoBean CredentialApplicationService credentialApplicationService;
    @MockitoBean CredentialVerificationService verificationService;
    @MockitoBean CredentialDocumentDownloadService documentDownloadService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void publicVerifyIsAccessibleWithoutBearerAndDoesNotExposeHashes() throws Exception {
        when(verificationService.verify(any())).thenReturn(verification());

        mockMvc.perform(post("/api/v1/credentials/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credentialNo\":\"CERT-VERIFY-001\",\"verificationType\":\"API\",\"requesterType\":\"SYSTEM\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.result").value("VALID"))
            .andExpect(jsonPath("$.data.presentedHash").doesNotExist())
            .andExpect(jsonPath("$.data.requestIp").doesNotExist())
            .andExpect(OpenApiValidationMatchers.openApi().isValid(OpenApiContract.VALIDATOR));
    }

    @Test
    void verifyRejectsMissingEnumFieldsWithValidationErrorNotServerError() throws Exception {
        // Set.of(...) 는 contains(null) 에서 NullPointerException 을 던지므로,
        // null 을 먼저 거르지 않으면 필수 필드 누락이 422 가 아니라 500 으로 나간다.
        mockMvc.perform(post("/api/v1/credentials/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credentialNo\":\"CERT-VERIFY-001\"}"))
            .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/credentials/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credentialNo\":\"CERT-VERIFY-001\",\"verificationType\":\"API\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void verifyRequiresExactlyOneIdentifierAndHistoryRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/credentials/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credentialNo\":\"CERT-VERIFY-001\",\"credentialHash\":\"" + "a".repeat(64)
                    + "\",\"verificationType\":\"API\",\"requesterType\":\"INDIVIDUAL\"}"))
            .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/v1/credentials/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credentialNo\":\"" + "A".repeat(101)
                    + "\",\"verificationType\":\"API\",\"requesterType\":\"INDIVIDUAL\"}"))
            .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/credentials/{id}/verifications", CREDENTIAL_ID))
            .andExpect(status().isUnauthorized());

        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(verificationService.list(any(), any(Integer.class), any(Integer.class), any(String.class), any()))
            .thenReturn(new CredentialVerificationPage(List.of(verification()), 0, 20, 1, 1));
        mockMvc.perform(get("/api/v1/credentials/{id}/verifications", CREDENTIAL_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Authorization", "Bearer credential-task9-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].result").value("VALID"))
            .andExpect(jsonPath("$.data[0].presentedHash").doesNotExist());
    }

    private CredentialVerification verification() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        return new CredentialVerification(UUID.randomUUID(), CREDENTIAL_ID, "CERT-VERIFY-001", null,
            "API", "INDIVIDUAL", null, CredentialVerificationResult.VALID, now, null, null,
            "{}", now);
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }
}
