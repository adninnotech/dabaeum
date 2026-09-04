package com.adn.dabaeum.course.api;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseSessionApplicationService;
import com.adn.dabaeum.course.application.CourseSessionPage;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
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
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseSessionController.class)
@AutoConfigureRestDocs(uriScheme = "https", uriHost = "api.dabaeum.local", uriPort = 443)
@Import({
    CourseSessionApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CourseSessionDocumentationTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID SESSION_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final String REQUEST_ID =
        "9b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired MockMvc mockMvc;
    @MockitoBean CourseSessionApplicationService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void documentsSessionListAndCreate() throws Exception {
        when(service.list(any())).thenReturn(new CourseSessionPage(
            List.of(session()), 0, 20, 1, 1));
        mockMvc.perform(get("/api/v1/courses/{courseId}/sessions", COURSE_ID)
                .queryParam("page", "0").queryParam("size", "20")
                .queryParam("sort", "createdAt,desc")
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("course-session-list", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                queryParameters(
                    parameterWithName("page").description("0부터 시작하는 페이지"),
                    parameterWithName("size").description("페이지 크기"),
                    parameterWithName("sort").description("정렬 필드와 방향")),
                sessionResponseFields(true)));

        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(service.create(any(), any())).thenReturn(session());
        mockMvc.perform(post("/api/v1/courses/{courseId}/sessions", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionNo\":1,\"startsAt\":\"2026-09-01T01:00:00Z\",\"endsAt\":\"2026-09-01T02:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location",
                "/api/v1/sessions/" + SESSION_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("course-session-create", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("sessionNo").description("회차 번호"),
                    fieldWithPath("startsAt").description("시작 시각"),
                    fieldWithPath("endsAt").description("종료 시각")),
                sessionResponseFields(false)));
    }

    @Test
    void documentsSessionGetAndUpdate() throws Exception {
        when(service.get(SESSION_ID)).thenReturn(session());
        mockMvc.perform(get("/api/v1/sessions/{sessionId}", SESSION_ID)
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("course-session-get", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()), sessionResponseFields(false)));

        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(service.update(any(), any())).thenReturn(session());
        mockMvc.perform(put("/api/v1/sessions/{sessionId}", SESSION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":\"변경 장소\",\"status\":\"OPEN\"}"))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("course-session-update", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("location").description("장소"),
                    fieldWithPath("status").description("다음 상태")),
                sessionResponseFields(false)));
    }

    private ResponseFieldsSnippet sessionResponseFields(boolean page) {
        String prefix = page ? "data[]" : "data";
        var fields = new java.util.ArrayList<org.springframework.restdocs.payload.FieldDescriptor>();
        fields.addAll(List.of(
            fieldWithPath(prefix + ".id").description("회차 ID"),
            fieldWithPath(prefix + ".courseId").description("과정 ID"),
            fieldWithPath(prefix + ".sessionNo").description("회차 번호"),
            fieldWithPath(prefix + ".startsAt").description("시작 시각"),
            fieldWithPath(prefix + ".endsAt").description("종료 시각"),
            fieldWithPath(prefix + ".location").optional().description("장소"),
            fieldWithPath(prefix + ".attendanceOpensAt").optional().description("출석 시작"),
            fieldWithPath(prefix + ".attendanceClosesAt").optional().description("출석 종료"),
            fieldWithPath(prefix + ".status").description("회차 상태"),
            fieldWithPath(prefix + ".createdAt").description("생성 시각"),
            fieldWithPath(prefix + ".updatedAt").description("수정 시각"),
            fieldWithPath("meta.requestId").description("요청 추적 UUID"),
            fieldWithPath("meta.timestamp").description("응답 시각")));
        if (page) {
            fields.addAll(List.of(
                fieldWithPath("page.page").description("현재 페이지"),
                fieldWithPath("page.size").description("페이지 크기"),
                fieldWithPath("page.totalElements").description("전체 요소 수"),
                fieldWithPath("page.totalPages").description("전체 페이지 수")));
        }
        return responseFields(fields.toArray(new org.springframework.restdocs.payload.FieldDescriptor[0]));
    }

    private AuthenticatedUserContext managerContext() {
        return new AuthenticatedUserContext(UUID.fromString(
            "11111111-1111-1111-1111-111111111111"), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }

    private CourseSession session() {
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        return new CourseSession(SESSION_ID, COURSE_ID, 1,
            Instant.parse("2026-09-01T01:00:00Z"),
            Instant.parse("2026-09-01T02:00:00Z"), "서울 교육장", null, null,
            CourseSessionStatus.OPEN, now, now);
    }
}
