package com.adn.dabaeum.role.api;

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

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.role.application.RoleApplicationService;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
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

@WebMvcTest(RoleController.class)
@AutoConfigureRestDocs(
    uriScheme = "https",
    uriHost = "api.dabaeum.local",
    uriPort = 443
)
@Import({
    RoleApiMapper.class,
    AuthorizationPolicy.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class RoleDocumentationTest {

    private static final UUID USER_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID ROLE_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555"
    );
    private static final String REQUEST_ID =
        "7b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RoleApplicationService service;

    @Test
    void documentsRoleList() throws Exception {
        when(service.list(USER_ID)).thenReturn(List.of(assignment()));

        mockMvc.perform(get("/api/v1/users/{userId}/roles", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data[0].id")
                .value(ROLE_ID.toString()))
            .andDo(document(
                "role-list",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                roleListResponseFields()
            ));
    }

    @Test
    void documentsRoleAssignment() throws Exception {
        when(service.assign(any())).thenReturn(assignment());

        mockMvc.perform(post("/api/v1/users/{userId}/roles", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "role": "INSTRUCTOR",
                      "institutionId": "55555555-5555-5555-5555-555555555555"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                "https://api.dabaeum.local/api/v1/users/" + USER_ID
                    + "/roles/" + ROLE_ID
            ))
            .andDo(document(
                "role-assignment",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("role").description("부여할 역할"),
                    fieldWithPath("institutionId")
                        .description("기관 범위 UUID(Platform Admin은 null)")
                ),
                roleResponseFields()
            ));
    }

    @Test
    void documentsRoleRevocation() throws Exception {
        when(service.revoke(USER_ID, ROLE_ID)).thenReturn(assignment());

        mockMvc.perform(delete(
                "/api/v1/users/{userId}/roles/{roleId}",
                USER_ID,
                ROLE_ID
            )
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andDo(document(
                "role-revocation",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                roleResponseFields()
            ));
    }

    private org.springframework.restdocs.payload.ResponseFieldsSnippet
        roleListResponseFields() {
        return responseFields(
            fieldWithPath("data[].id").description("Role assignment ID"),
            fieldWithPath("data[].role").description("역할"),
            fieldWithPath("data[].institutionId")
                .description("기관 범위 UUID"),
            fieldWithPath("data[].createdAt").description("생성 시각"),
            fieldWithPath("meta.requestId").description("요청 추적 UUID"),
            fieldWithPath("meta.timestamp").description("응답 시각")
        );
    }

    private org.springframework.restdocs.payload.ResponseFieldsSnippet
        roleResponseFields() {
        return responseFields(
            fieldWithPath("data.id").description("Role assignment ID"),
            fieldWithPath("data.role").description("역할"),
            fieldWithPath("data.institutionId")
                .description("기관 범위 UUID"),
            fieldWithPath("data.createdAt").description("생성 시각"),
            fieldWithPath("meta.requestId").description("요청 추적 UUID"),
            fieldWithPath("meta.timestamp").description("응답 시각")
        );
    }

    private UserRoleAssignment assignment() {
        return new UserRoleAssignment(
            ROLE_ID,
            USER_ID,
            INSTITUTION_ID,
            UserRole.INSTRUCTOR,
            Instant.parse("2026-08-04T00:00:00Z")
        );
    }
}
