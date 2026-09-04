package com.adn.dabaeum.user.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.user.application.UserApplicationService;
import com.adn.dabaeum.user.application.UserPage;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserStatus;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(UserController.class)
@AutoConfigureRestDocs(
    uriScheme = "https",
    uriHost = "api.dabaeum.local",
    uriPort = 443
)
@Import({
    UserApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class UserDocumentationTest {

    private static final Path CONTRACT = Path.of(
        "docs/api/dabaeum-api-v1.yaml"
    ).toAbsolutePath();
    private static final UUID USER_ID = UUID.fromString(
        "77777777-7777-7777-7777-777777777777"
    );
    private static final String REQUEST_ID =
        "cb706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserApplicationService service;

    @Test
    void documentsUserCreate() throws Exception {
        when(service.create(any())).thenReturn(sampleUser());

        mockMvc.perform(post("/api/v1/users")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody()))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string(
                "Location",
                "https://api.dabaeum.local/api/v1/users/" + USER_ID
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.data.name").value("문서 사용자"))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "user-create",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("name").description("사용자명"),
                    fieldWithPath("email").description("이메일"),
                    fieldWithPath("phone").description("연락처"),
                    fieldWithPath("birthDate").description("생년월일"),
                    fieldWithPath("status").description("사용자 상태")
                ),
                userResponseFields(false)
            ));
    }

    @Test
    void documentsUserList() throws Exception {
        when(service.list(any())).thenReturn(new UserPage(
            List.of(sampleUser()),
            0,
            20,
            1,
            1
        ));

        mockMvc.perform(get("/api/v1/users")
                .queryParam("page", "0")
                .queryParam("size", "20")
                .queryParam("sort", "createdAt,desc")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data[0].id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.page.totalPages").value(1))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "user-list",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                queryParameters(
                    parameterWithName("page").description("0부터 시작하는 페이지"),
                    parameterWithName("size").description("페이지 크기"),
                    parameterWithName("sort").description("정렬 필드와 방향")
                ),
                userResponseFields(true)
            ));
    }

    @Test
    void documentsUserGet() throws Exception {
        when(service.get(USER_ID)).thenReturn(sampleUser());

        mockMvc.perform(get("/api/v1/users/{userId}", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "user-get",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                userResponseFields(false)
            ));
    }

    @Test
    void documentsUserUpdate() throws Exception {
        when(service.updateProfile(any())).thenReturn(sampleUser());

        mockMvc.perform(put("/api/v1/users/{userId}", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "문서 사용자 수정",
                      "email": null,
                      "phone": "010-2222-3333"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "user-update",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("name").description("사용자명"),
                    fieldWithPath("email").description("null 지정 시 값을 지움"),
                    fieldWithPath("phone").description("연락처")
                ),
                userResponseFields(false)
            ));
    }

    @Test
    void documentsUserStatus() throws Exception {
        when(service.changeStatus(any())).thenReturn(withdrawnUser());

        mockMvc.perform(patch("/api/v1/users/{userId}/status", USER_ID)
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"WITHDRAWN\"}"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
            .andExpect(jsonPath("$.data.withdrawnAt").exists())
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "user-status",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("status").description("변경할 사용자 상태")
                ),
                userResponseFields(false)
            ));
    }

    @Test
    void documentsUserMeGet() throws Exception {
        when(service.getMe(USER_ID)).thenReturn(sampleUser());

        mockMvc.perform(get("/api/v1/users/me")
                .with(principal(USER_ID))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "user-me-get",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                userResponseFields(false)
            ));
    }

    @Test
    void documentsUserMeUpdate() throws Exception {
        when(service.updateMe(any(), any())).thenReturn(sampleUser());

        mockMvc.perform(put("/api/v1/users/me")
                .with(principal(USER_ID))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "본인 변경",
                      "email": "me@example.com",
                      "birthDate": "2001-02-03"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON
            ))
            .andExpect(header().string(
                RequestIdFilter.HEADER_NAME,
                REQUEST_ID
            ))
            .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "user-me-update",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("name").description("사용자명"),
                    fieldWithPath("email").description("이메일"),
                    fieldWithPath("birthDate").description("생년월일")
                ),
                userResponseFields(false)
            ));
    }

    private RequestPostProcessor principal(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedUserPrincipal(userId, Set.of("USER")),
            "N/A",
            Set.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }

    private String createBody() {
        return """
            {
              "name": "문서 사용자",
              "email": "user@example.com",
              "phone": "010-1234-5678",
              "birthDate": "2000-01-02",
              "status": "ACTIVE"
            }
            """;
    }

    private ResponseFieldsSnippet userResponseFields(boolean page) {
        if (page) {
            return responseFields(
                fieldWithPath("data[].id").description("사용자 ID"),
                fieldWithPath("data[].name").description("사용자명"),
                fieldWithPath("data[].email").description("이메일"),
                fieldWithPath("data[].phone").description("연락처"),
                fieldWithPath("data[].birthDate").description("생년월일"),
                fieldWithPath("data[].status").description("사용자 상태"),
                fieldWithPath("data[].withdrawnAt").description("탈퇴 시각"),
                fieldWithPath("data[].createdAt").description("생성 시각"),
                fieldWithPath("data[].updatedAt").description("수정 시각"),
                fieldWithPath("data[].career").optional().description("경력"),
                fieldWithPath("data[].introduction").optional().description("소개"),
                fieldWithPath("data[].profileImageId").optional()
                    .description("프로필 이미지 파일 ID"),
                fieldWithPath("page.page").description("현재 페이지"),
                fieldWithPath("page.size").description("페이지 크기"),
                fieldWithPath("page.totalElements").description("전체 요소 수"),
                fieldWithPath("page.totalPages").description("전체 페이지 수"),
                fieldWithPath("meta.requestId").description("요청 추적 UUID"),
                fieldWithPath("meta.timestamp").description("응답 시각")
            );
        }
        return responseFields(
            fieldWithPath("data.id").description("사용자 ID"),
            fieldWithPath("data.name").description("사용자명"),
            fieldWithPath("data.email").description("이메일"),
            fieldWithPath("data.phone").description("연락처"),
            fieldWithPath("data.birthDate").description("생년월일"),
            fieldWithPath("data.status").description("사용자 상태"),
            fieldWithPath("data.withdrawnAt").description("탈퇴 시각"),
            fieldWithPath("data.createdAt").description("생성 시각"),
            fieldWithPath("data.updatedAt").description("수정 시각"),
            fieldWithPath("data.career").optional().description("경력"),
            fieldWithPath("data.introduction").optional().description("소개"),
            fieldWithPath("data.profileImageId").optional()
                .description("프로필 이미지 파일 ID"),
            fieldWithPath("meta.requestId").description("요청 추적 UUID"),
            fieldWithPath("meta.timestamp").description("응답 시각")
        );
    }

    private User sampleUser() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z");
        return new User(
            USER_ID,
            "문서 사용자",
            "user@example.com",
            "010-1234-5678",
            LocalDate.of(2000, 1, 2),
            UserStatus.ACTIVE,
            null,
            instant,
            instant
        );
    }

    private User withdrawnUser() {
        Instant withdrawnAt = Instant.parse("2026-08-04T01:00:00Z");
        return new User(
            USER_ID,
            "문서 사용자",
            "user@example.com",
            "010-1234-5678",
            LocalDate.of(2000, 1, 2),
            UserStatus.WITHDRAWN,
            withdrawnAt,
            Instant.parse("2026-08-01T00:00:00Z"),
            withdrawnAt
        );
    }
}
