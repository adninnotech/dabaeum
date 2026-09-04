package com.adn.dabaeum.credential.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedRequestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.credential.application.CredentialApplicationService;
import com.adn.dabaeum.credential.application.CredentialDocumentDownloadService;
import com.adn.dabaeum.credential.application.CredentialPage;
import com.adn.dabaeum.credential.application.CredentialVerificationService;
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
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CredentialController.class)
@AutoConfigureRestDocs(uriScheme = "https", uriHost = "api.dabaeum.local", uriPort = 443)
@Import({CredentialApiMapper.class, ApiExceptionHandler.class, ClockConfiguration.class,
    SecurityConfiguration.class, JacksonConfiguration.class, RequestIdFilter.class})
class CredentialDocumentationTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID COMPLETION_ID = UUID.fromString(
        "10000000-0000-0000-0000-000000000001");
    private static final UUID CREDENTIAL_ID = UUID.fromString(
        "70000000-0000-0000-0000-000000000007");
    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");
    private static final UUID GROUP_ID = UUID.fromString(
        "60000000-0000-0000-0000-000000000006");
    private static final UUID COURSE_ID = UUID.fromString(
        "30000000-0000-0000-0000-000000000003");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "50000000-0000-0000-0000-000000000005");

    @Autowired MockMvc mockMvc;
    @MockitoBean CredentialApplicationService service;
    @MockitoBean CredentialVerificationService verificationService;
    @MockitoBean CredentialDocumentDownloadService documentDownloadService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void documentsCredentialIssueGetAndUserList() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.issue(any())).thenReturn(credential());
        when(service.get(any(), any())).thenReturn(credentialView());
        when(service.listByUser(any())).thenReturn(new CredentialPage(
            List.of(credentialView()), 0, 20, 1, 1));

        mockMvc.perform(post("/api/v1/completions/{id}/credentials", COMPLETION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Authorization", "Bearer credential-task5-token")
                .header("Idempotency-Key", "credential-doc-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"validUntil\":\"2027-08-07T00:00:00Z\"}"))
            .andExpect(status().isAccepted())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("credential-issue", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestHeaders(headerWithName("Idempotency-Key")
                    .description("중복 발급 요청을 막는 8~128자 키")),
                responseHeaders(headerWithName("Location")
                    .description("발급 상태를 조회할 Credential 상세 URI")),
                relaxedRequestFields(fieldWithPath("validUntil")
                    .optional().description("선택적 유효기간(RFC3339); 생략하면 무기한")),
                relaxedResponseFields(fieldWithPath("data.status")
                    .description("발급 상태; 202 응답 직후 PENDING이며 상세 API로 polling"))));

        mockMvc.perform(get("/api/v1/credentials/{id}", CREDENTIAL_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Authorization", "Bearer credential-task5-token"))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("credential-get", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                relaxedResponseFields(fieldWithPath("data.status")
                    .description("PENDING/ISSUING/ISSUED/FAILED 상태와 polling 의미"),
                    fieldWithPath("data.courseTitle").optional()
                        .description("발급 근거가 된 과정명; 원장에 앵커되는 VC payload에는 포함되지 않는다"),
                    fieldWithPath("data.institutionName").optional()
                        .description("과정을 개설한 기관명"))));

        mockMvc.perform(get("/api/v1/users/{id}/credentials", USER_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Authorization", "Bearer credential-task5-token")
                .param("page", "0").param("size", "20").param("sort", "createdAt,desc"))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("credential-user-list", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                queryParameters(parameterWithName("page").description("0부터 시작하는 페이지"),
                    parameterWithName("size").description("페이지 크기(1~100)"),
                    parameterWithName("sort").description("createdAt,desc 등 허용된 정렬")),
                relaxedResponseFields(fieldWithPath("page")
                    .description("페이지 메타데이터"))));
    }

    @Test
    void documentsCredentialRevokeAndReissueAsynchronousRequests() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.revoke(any())).thenReturn(credential());
        when(service.reissue(any())).thenReturn(credential());

        mockMvc.perform(post("/api/v1/credentials/{id}/revoke", CREDENTIAL_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Authorization", "Bearer credential-task8-token")
                .header("Idempotency-Key", "credential-revoke-doc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"기관 정정 요청\"}"))
            .andExpect(status().isAccepted())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("credential-revoke", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestHeaders(headerWithName("Idempotency-Key")
                    .description("동일 폐기 요청 재시도를 묶는 8~128자 키")),
                relaxedRequestFields(fieldWithPath("reason")
                    .description("폐기 사유(1~1000자; Fabric 확정 전 DB Credential은 ISSUED 유지)")),
                relaxedResponseFields(fieldWithPath("data.status")
                    .description("202 직후 상태; Fabric 확정 후 REVOKED로 polling"))));

        mockMvc.perform(post("/api/v1/credentials/{id}/reissue", CREDENTIAL_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Authorization", "Bearer credential-task8-token")
                .header("Idempotency-Key", "credential-reissue-doc")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"정정 재발급\",\"validUntil\":\"2027-08-07T00:00:00Z\"}"))
            .andExpect(status().isAccepted())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("credential-reissue", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestHeaders(headerWithName("Idempotency-Key")
                    .description("동일 재발급 요청 재시도를 묶는 8~128자 키")),
                relaxedRequestFields(fieldWithPath("reason")
                    .description("재발급 사유(1~1000자)"),
                    fieldWithPath("validUntil").optional()
                        .description("선택적 유효기간(RFC3339)")),
                relaxedResponseFields(fieldWithPath("data.status")
                    .description("202 직후 신규 Credential은 PENDING; 기존 Credential은 Fabric 확정 전 유효"))));
    }

    private CredentialView credentialView() {
        return new CredentialView(credential(), new CredentialCourseView(
            CREDENTIAL_ID, COURSE_ID, "AI 기초", "AI-101", "대구평생교육진흥원"));
    }

    private Credential credential() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        return new Credential(CREDENTIAL_ID, GROUP_ID, null,
            "CERT-70000000000000000000000000000007", 1, null, null,
            "LIFELONG_EDUCATION_COMPLETION", CredentialStatus.PENDING,
            null, Instant.parse("2027-08-07T00:00:00Z"), null, null, null, null,
            null, null, null, now, now);
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }
}
