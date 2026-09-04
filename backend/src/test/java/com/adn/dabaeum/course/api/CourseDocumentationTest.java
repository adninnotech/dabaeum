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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseApplicationService;
import com.adn.dabaeum.course.application.CoursePage;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.math.BigDecimal;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseController.class)
@AutoConfigureRestDocs(uriScheme = "https", uriHost = "api.dabaeum.local", uriPort = 443)
@Import({
    CourseApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CourseDocumentationTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final String REQUEST_ID =
        "8b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CourseApplicationService service;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Test
    void documentsCourseList() throws Exception {
        when(service.list(any())).thenReturn(new CoursePage(
            List.of(course()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/courses")
                .queryParam("page", "0")
                .queryParam("size", "20")
                .queryParam("sort", "createdAt,desc")
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "course-list",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                queryParameters(
                    parameterWithName("page").description("0부터 시작하는 페이지"),
                    parameterWithName("size").description("페이지 크기"),
                    parameterWithName("sort").description("정렬 필드와 방향")
                ),
                courseResponseFields(true)
            ));
    }

    @Test
    void documentsCourseCreateAndGet() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(service.create(any(), any())).thenReturn(course());

        mockMvc.perform(post("/api/v1/courses")
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullCreateJson()))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location", "https://api.dabaeum.local/api/v1/courses/" + COURSE_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "course-create",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("institutionId").description("기관 ID"),
                    fieldWithPath("courseCode").description("과정 코드"),
                    fieldWithPath("title").description("과정명"),
                    fieldWithPath("description").optional().description("상세 설명"),
                    fieldWithPath("category").optional().description("분류"),
                    fieldWithPath("educationType").description("교육 유형"),
                    fieldWithPath("startDate").description("시작일"),
                    fieldWithPath("endDate").description("종료일"),
                    fieldWithPath("recruitStartDate").optional().description("모집 시작일"),
                    fieldWithPath("recruitEndDate").optional().description("모집 종료일"),
                    fieldWithPath("capacity").description("정원"),
                    fieldWithPath("location").optional().description("교육 장소"),
                    fieldWithPath("onlineUrl").optional().description("온라인 URL"),
                    fieldWithPath("creditBankEligible").optional().description("학점은행제 대상 여부"),
                    fieldWithPath("creditValue").optional().description("학점 값")
                ),
                courseResponseFields(false)
            ));

        when(service.get(COURSE_ID)).thenReturn(course());
        mockMvc.perform(get("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "course-get",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                courseResponseFields(false)
            ));
    }

    @Test
    void documentsCourseUpdate() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(service.update(any(), any())).thenReturn(course());

        mockMvc.perform(put("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"수정 과정", "description":null, "status":"IN_PROGRESS"}
                    """))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "course-update",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(
                    fieldWithPath("title").description("과정명"),
                    fieldWithPath("description").description("null 지정 시 값을 지움"),
                    fieldWithPath("status").description("허용된 다음 상태")
                ),
                courseResponseFields(false)
            ));
    }

    @Test
    void documentsCoursePublishAndClose() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(service.publish(any(), any())).thenReturn(course());
        when(service.close(any(), any())).thenReturn(course());

        mockMvc.perform(post("/api/v1/courses/{courseId}/publish", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "course-publish",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                courseResponseFields(false)
            ));

        mockMvc.perform(post("/api/v1/courses/{courseId}/close", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document(
                "course-close",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                courseResponseFields(false)
            ));
    }

    private ResponseFieldsSnippet courseResponseFields(boolean page) {
        String prefix = page ? "data[]" : "data";
        var descriptors = new java.util.ArrayList<org.springframework.restdocs.payload.FieldDescriptor>();
        descriptors.addAll(java.util.List.of(
            fieldWithPath(prefix + ".id").description("과정 ID"),
            fieldWithPath(prefix + ".institutionId").description("기관 ID"),
            fieldWithPath(prefix + ".courseCode").description("과정 코드"),
            fieldWithPath(prefix + ".title").description("과정명"),
            fieldWithPath(prefix + ".description").optional().description("상세 설명"),
            fieldWithPath(prefix + ".category").optional().description("분류"),
            fieldWithPath(prefix + ".educationType").description("교육 유형"),
            fieldWithPath(prefix + ".startDate").description("시작일"),
            fieldWithPath(prefix + ".endDate").description("종료일"),
            fieldWithPath(prefix + ".recruitStartDate").optional().description("모집 시작일"),
            fieldWithPath(prefix + ".recruitEndDate").optional().description("모집 종료일"),
            fieldWithPath(prefix + ".capacity").description("정원"),
            fieldWithPath(prefix + ".location").optional().description("교육 장소"),
            fieldWithPath(prefix + ".onlineUrl").optional().description("온라인 URL"),
            fieldWithPath(prefix + ".creditBankEligible").description("학점은행제 대상 여부"),
            fieldWithPath(prefix + ".creditValue").optional().description("학점 값"),
            fieldWithPath(prefix + ".status").description("과정 상태"),
            fieldWithPath(prefix + ".createdAt").description("생성 시각"),
            fieldWithPath(prefix + ".updatedAt").description("수정 시각"),
            fieldWithPath(prefix + ".thumbnailFileId").optional().description("썸네일 파일 ID"),
            fieldWithPath("meta.requestId").description("요청 추적 UUID"),
            fieldWithPath("meta.timestamp").description("응답 시각")
        ));
        if (page) {
            descriptors.addAll(java.util.List.of(
                fieldWithPath("page.page").description("현재 페이지"),
                fieldWithPath("page.size").description("페이지 크기"),
                fieldWithPath("page.totalElements").description("전체 요소 수"),
                fieldWithPath("page.totalPages").description("전체 페이지 수")
            ));
        }
        return responseFields(descriptors.toArray(new org.springframework.restdocs.payload.FieldDescriptor[0]));
    }

    private AuthenticatedUserContext managerContext() {
        return new AuthenticatedUserContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID))
        );
    }

    private String fullCreateJson() {
        return """
            {
              "institutionId": "22222222-2222-2222-2222-222222222222",
              "courseCode": "COURSE-001",
              "title": "과정 제목",
              "description": "상세 설명",
              "category": "개발",
              "educationType": "HYBRID",
              "startDate": "2026-09-01",
              "endDate": "2026-10-31",
              "recruitStartDate": "2026-08-01",
              "recruitEndDate": "2026-08-25",
              "capacity": 30,
              "location": "서울 교육장",
              "onlineUrl": "https://example.test/course",
              "creditBankEligible": true,
              "creditValue": 12.50
            }
            """;
    }

    private Course course() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z");
        return new Course(
            COURSE_ID, INSTITUTION_ID, "COURSE-001", "과정 제목", "상세 설명", "개발",
            CourseEducationType.HYBRID, LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 10, 31), LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 25), 30, "서울 교육장",
            "https://example.test/course", true, new BigDecimal("12.50"),
            CourseStatus.IN_PROGRESS, instant, instant, null
        );
    }
}
