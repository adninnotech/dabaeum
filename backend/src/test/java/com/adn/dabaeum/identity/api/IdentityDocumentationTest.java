package com.adn.dabaeum.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.identity.application.IdentityApplicationService;
import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.identity.domain.UserIdentity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IdentityController.class)
@AutoConfigureRestDocs(
    uriScheme = "https",
    uriHost = "api.dabaeum.local",
    uriPort = 443
)
@Import({
    IdentityApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class IdentityDocumentationTest {

    private static final UUID USER_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID IDENTITY_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444"
    );
    private static final String REQUEST_ID =
        "7b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    IdentityApplicationService service;

    @Test
    void documentsIdentityList() throws Exception {
        when(service.list(USER_ID)).thenReturn(List.of(identity()));

        mockMvc.perform(get("/api/v1/users/{userId}/identities", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data[0].id")
                .value(IDENTITY_ID.toString()))
            .andDo(document(
                "identity-list",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                identityListResponseFields()
            ));
    }

    @Test
    void documentsIdentityLink() throws Exception {
        when(service.link(any())).thenReturn(identity());

        mockMvc.perform(post("/api/v1/users/{userId}/identities", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "provider": "DADAEGU",
                      "providerSubject": "subject-raw-value",
                      "externalDid": "did:example:123",
                      "verified": true
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                "https://api.dabaeum.local/api/v1/users/" + USER_ID
                    + "/identities/" + IDENTITY_ID
            ))
            .andDo(document(
                "identity-link",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("provider").description("Identity Provider"),
                    fieldWithPath("providerSubject")
                        .description("Provider 내부 식별자"),
                    fieldWithPath("externalDid")
                        .description("검증된 외부 DID"),
                    fieldWithPath("verified")
                        .description("검증 완료 여부")
                ),
                identityResponseFields()
            ));
    }

    @Test
    void documentsIdentityUnlink() throws Exception {
        when(service.unlink(USER_ID, IDENTITY_ID)).thenReturn(identity());

        mockMvc.perform(delete(
                "/api/v1/users/{userId}/identities/{identityId}",
                USER_ID,
                IDENTITY_ID
            )
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andDo(document(
                "identity-unlink",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                identityResponseFields()
            ));
    }

    private org.springframework.restdocs.payload.ResponseFieldsSnippet
        identityListResponseFields() {
        return responseFields(
            fieldWithPath("data[].id").description("Identity ID"),
            fieldWithPath("data[].provider").description("Identity Provider"),
            fieldWithPath("data[].verifiedAt")
                .description("검증 시각"),
            fieldWithPath("data[].createdAt").description("생성 시각"),
            fieldWithPath("data[].updatedAt").description("수정 시각"),
            fieldWithPath("meta.requestId").description("요청 추적 UUID"),
            fieldWithPath("meta.timestamp").description("응답 시각")
        );
    }

    private org.springframework.restdocs.payload.ResponseFieldsSnippet
        identityResponseFields() {
        return responseFields(
            fieldWithPath("data.id").description("Identity ID"),
            fieldWithPath("data.provider").description("Identity Provider"),
            fieldWithPath("data.verifiedAt").description("검증 시각"),
            fieldWithPath("data.createdAt").description("생성 시각"),
            fieldWithPath("data.updatedAt").description("수정 시각"),
            fieldWithPath("meta.requestId").description("요청 추적 UUID"),
            fieldWithPath("meta.timestamp").description("응답 시각")
        );
    }

    private UserIdentity identity() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z");
        return new UserIdentity(
            IDENTITY_ID,
            USER_ID,
            IdentityProvider.DADAEGU,
            "subject-raw-value",
            "did:example:123",
            instant,
            "{\"private\":true}",
            instant,
            instant
        );
    }
}
