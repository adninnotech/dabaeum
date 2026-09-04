package com.adn.dabaeum.credential.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedRequestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.payload.JsonFieldType.STRING;
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
import com.adn.dabaeum.credential.application.CredentialVerificationPage;
import com.adn.dabaeum.credential.application.CredentialVerificationService;
import com.adn.dabaeum.credential.domain.CredentialVerification;
import com.adn.dabaeum.credential.domain.CredentialVerificationResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
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
class CredentialVerificationDocumentationTest {

    private static final UUID CREDENTIAL_ID = UUID.fromString(
        "70000000-0000-0000-0000-000000000007");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "50000000-0000-0000-0000-000000000005");

    @Autowired MockMvc mockMvc;
    @MockitoBean CredentialApplicationService credentialApplicationService;
    @MockitoBean CredentialVerificationService verificationService;
    @MockitoBean CredentialDocumentDownloadService documentDownloadService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void documentsPublicVerifyAndAuthenticatedHistory() throws Exception {
        when(verificationService.verify(any())).thenReturn(verification());
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(verificationService.list(any(), any(Integer.class), any(Integer.class), any(String.class), any()))
            .thenReturn(new CredentialVerificationPage(List.of(verification()), 0, 20, 1, 1));

        mockMvc.perform(post("/api/v1/credentials/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credentialNo\":\"CERT-VERIFY-001\",\"verificationType\":\"API\",\"requesterType\":\"INDIVIDUAL\"}"))
            .andExpect(status().isOk())
            .andDo(document("credential-verify", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                relaxedRequestFields(
                    fieldWithPath("credentialNo").description("Credential 번호 또는 credentialHash 중 정확히 하나"),
                    fieldWithPath("credentialHash").type(STRING).optional().description("credentialNo와 배타적인 64자 해시"),
                    fieldWithPath("verificationType").type(STRING).description("QR/API/ADMIN"),
                    fieldWithPath("requesterType").type(STRING).description("요청자 유형; 공개 감사에는 INDIVIDUAL로 기록"),
                    fieldWithPath("requesterId").type(STRING).optional().description("공개 검증에서는 저장하지 않음")),
                relaxedResponseFields(
                    fieldWithPath("data.result").description("VALID/INVALID/REVOKED/SUPERSEDED/EXPIRED/NOT_FOUND/ERROR"),
                    fieldWithPath("meta.requestId").description("요청 추적 ID"))));

        mockMvc.perform(get("/api/v1/credentials/{id}/verifications", CREDENTIAL_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .param("page", "0").param("size", "20").param("sort", "verifiedAt,desc"))
            .andExpect(status().isOk())
            .andDo(document("credential-verification-list", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                queryParameters(parameterWithName("page").description("0부터 시작하는 페이지"),
                    parameterWithName("size").description("페이지 크기(1~100)"),
                    parameterWithName("sort").description("verifiedAt,desc 등 허용된 정렬")),
                relaxedResponseFields(fieldWithPath("data[].result")
                    .description("검증 판정; 해시·IP·metadata는 응답에 포함하지 않음"))));
    }

    private CredentialVerification verification() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        return new CredentialVerification(UUID.randomUUID(), CREDENTIAL_ID, "CERT-VERIFY-001", null,
            "API", "INDIVIDUAL", null, CredentialVerificationResult.VALID, now, null, null,
            "{}", now);
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }
}
