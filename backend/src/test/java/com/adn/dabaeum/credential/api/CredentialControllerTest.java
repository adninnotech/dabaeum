package com.adn.dabaeum.credential.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.adn.dabaeum.credential.application.CredentialPage;
import com.adn.dabaeum.credential.application.CredentialDocumentDownloadService;
import com.adn.dabaeum.credential.application.CredentialVerificationService;
import com.adn.dabaeum.credential.application.IssueCredentialCommand;
import com.adn.dabaeum.credential.application.ListUserCredentialsQuery;
import com.adn.dabaeum.credential.domain.Credential;
import com.adn.dabaeum.credential.domain.CredentialCourseView;
import com.adn.dabaeum.credential.domain.CredentialStatus;
import com.adn.dabaeum.credential.domain.CredentialView;
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
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=credential-task5-token")
@Import({
    CredentialApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CredentialControllerTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID CREDENTIAL_ID = UUID.fromString(
        "70000000-0000-0000-0000-000000000007");
    private static final UUID GROUP_ID = UUID.fromString(
        "60000000-0000-0000-0000-000000000006");
    private static final UUID COMPLETION_ID = UUID.fromString(
        "10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "50000000-0000-0000-0000-000000000005");
    private static final UUID COURSE_ID = UUID.fromString(
        "30000000-0000-0000-0000-000000000003");
    private static final String REQUEST_ID = "1f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1";

    @Autowired MockMvc mockMvc;
    @MockitoBean CredentialApplicationService service;
    @MockitoBean CredentialVerificationService verificationService;
    @MockitoBean CredentialDocumentDownloadService documentDownloadService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void issuesGetsAndListsCredentialWithOpenApiEnvelopes() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.issue(any(IssueCredentialCommand.class))).thenReturn(pendingCredential());
        when(service.get(CREDENTIAL_ID, manager())).thenReturn(pendingCredentialView());
        when(service.listByUser(any(ListUserCredentialsQuery.class))).thenReturn(
            new CredentialPage(java.util.List.of(pendingCredentialView()), 0, 20, 1, 1));

        mockMvc.perform(post("/api/v1/completions/{id}/credentials", COMPLETION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Idempotency-Key", "credential-issue-1")
                .header("Authorization", "Bearer credential-task5-token")
                .contentType(MediaType.APPLICATION_JSON)
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/v1/credentials/" + CREDENTIAL_ID))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        mockMvc.perform(get("/api/v1/credentials/{id}", CREDENTIAL_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Authorization", "Bearer credential-task5-token")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(CREDENTIAL_ID.toString()))
            .andExpect(jsonPath("$.data.courseId").value(COURSE_ID.toString()))
            .andExpect(jsonPath("$.data.courseTitle").value("AI 기초"))
            .andExpect(jsonPath("$.data.courseCode").value("AI-101"))
            .andExpect(jsonPath("$.data.institutionName").value("대구평생교육진흥원"))
            .andExpect(jsonPath("$.data.vcPayload").doesNotExist())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        mockMvc.perform(get("/api/v1/users/{id}/credentials", USER_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Authorization", "Bearer credential-task5-token")
                .param("page", "0")
                .param("size", "20")
                .param("sort", "createdAt,desc")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].credentialNo").value("CERT-70000000000000000000000000000007"))
            .andExpect(jsonPath("$.data[0].courseTitle").value("AI 기초"))
            .andExpect(jsonPath("$.data[0].institutionName").value("대구평생교육진흥원"))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).issue(any(IssueCredentialCommand.class));
    }

    @Test
    void listsCurrentUserCredentialsWithTheAuthenticatedUserId() throws Exception {
        AuthenticatedUserContext learner = learner();
        when(currentUserProvider.requireContext()).thenReturn(learner);
        when(service.listByUser(any(ListUserCredentialsQuery.class))).thenReturn(
            new CredentialPage(List.of(pendingCredentialView()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/users/me/credentials")
                .with(user("learner").roles("LEARNER"))
                .header("Authorization", "Bearer credential-task5-token")
                .param("page", "0")
                .param("size", "20")
                .param("sort", "createdAt,desc")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].credentialNo")
                .value("CERT-70000000000000000000000000000007"))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        org.mockito.ArgumentCaptor<ListUserCredentialsQuery> query =
            org.mockito.ArgumentCaptor.forClass(ListUserCredentialsQuery.class);
        verify(service).listByUser(query.capture());
        org.assertj.core.api.Assertions.assertThat(query.getValue().userId()).isEqualTo(USER_ID);
        org.assertj.core.api.Assertions.assertThat(query.getValue().actor()).isEqualTo(learner);
    }

    @Test
    void rejectsUnauthenticatedCurrentUserCredentialRequests() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/credentials"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadsCompactCredentialDocumentWithNoStoreHeaders() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(documentDownloadService.download(CREDENTIAL_ID, manager())).thenReturn(
            new CredentialDocumentDownloadService.Download(
                "CERT-70000000000000000000000000000007", "header.payload.signature"));

        mockMvc.perform(get("/api/v1/credentials/{id}/document", CREDENTIAL_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/vc+jwt"))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"CERT-70000000000000000000000000000007.jwt\""))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string("header.payload.signature"));
    }

    @Test
    void acceptsMissingOrEmptyIssueBodyAndRejectsUnknownOrInvalidInput() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.issue(any(IssueCredentialCommand.class))).thenReturn(pendingCredential());

        mockMvc.perform(post("/api/v1/completions/{id}/credentials", COMPLETION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Idempotency-Key", "credential-empty-body"))
            .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/completions/{id}/credentials", COMPLETION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Idempotency-Key", "credential-unknown")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"unexpected\":true}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/completions/{id}/credentials", COMPLETION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Idempotency-Key", "credential-invalid-date")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"validUntil\":\"not-a-date\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsMissingBlankAndTooLongIdempotencyKey() throws Exception {
        for (String key : new String[] {null, " ", "x".repeat(129)}) {
            var request = post("/api/v1/completions/{id}/credentials", COMPLETION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"));
            if (key != null) {
                request.header("Idempotency-Key", key);
            }
            mockMvc.perform(request).andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsUnknownCredentialListSortWithBadRequest() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());

        mockMvc.perform(get("/api/v1/users/{id}/credentials", USER_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .param("sort", "foo,desc"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/users/{id}/credentials", USER_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .param("page", "2147483647")
                .param("size", "100"))
            .andExpect(status().isBadRequest());
    }

    private CredentialView pendingCredentialView() {
        return new CredentialView(pendingCredential(), new CredentialCourseView(
            CREDENTIAL_ID, COURSE_ID, "AI 기초", "AI-101", "대구평생교육진흥원"));
    }

    private Credential pendingCredential() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        return new Credential(CREDENTIAL_ID, GROUP_ID, null,
            "CERT-70000000000000000000000000000007", 1, null, null,
            "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
            null, Instant.parse("2027-08-07T00:00:00Z"), null, null, null, null,
            null, null, null, now, now);
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(UUID.fromString(
            "50000000-0000-0000-0000-000000000005"), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL", Set.of(
            new AuthenticatedRole("LEARNER", null)));
    }
}
