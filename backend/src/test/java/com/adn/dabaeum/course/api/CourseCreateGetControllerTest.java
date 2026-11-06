package com.adn.dabaeum.course.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseApplicationService;
import com.adn.dabaeum.course.application.CreateCourseCommand;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=course-task3-test-token")
@Import({
    CourseApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CourseCreateGetControllerTest {

    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID OTHER_INSTITUTION_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444"
    );

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CourseApplicationService service;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Test
    void createsCourseWithLocationAndOpenApiResponse() throws Exception {
        String requestId = "8b706c0e-96cf-4d0a-b357-d80752facf1c";
        when(currentUserProvider.requireContext()).thenReturn(globalAdmin());
        when(service.create(any(), any())).thenReturn(course());

        mockMvc.perform(post("/api/v1/courses")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullCreateJson()))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                "http://localhost/api/v1/courses/" + COURSE_ID))
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId))
            .andExpect(jsonPath("$.data.id").value(COURSE_ID.toString()))
            .andExpect(jsonPath("$.data.institutionId")
                .value(INSTITUTION_ID.toString()))
            .andExpect(jsonPath("$.data.courseCode").value("COURSE-001"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
            // 생성 응답은 방금 만든 Course 를 그대로 실을 뿐 집계를 다시 읽지 않는다 —
            // institutionName·mainInstructorName 키는 있고 값은 항상 null.
            .andExpect(jsonPath("$.data.institutionName").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.mainInstructorName").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.meta.requestId").value(requestId))
            .andExpect(jsonPath("$.meta.timestamp")
                .value(matchesPattern(".*\\+09:00$")));

        verify(service).create(any(CreateCourseCommand.class), any());
    }

    @Test
    void defaultsOmittedCreditBankEligibleToFalseInCommand() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(globalAdmin());
        when(service.create(any(), any())).thenReturn(course());

        mockMvc.perform(post("/api/v1/courses")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId": "22222222-2222-2222-2222-222222222222",
                      "courseCode": "COURSE-OMITTED",
                      "title": "과정",
                      "educationType": "ONLINE",
                      "startDate": "2026-09-01",
                      "endDate": "2026-09-01",
                      "capacity": 1
                    }
                    """))
            .andExpect(status().isCreated());

        ArgumentCaptor<CreateCourseCommand> command =
            ArgumentCaptor.forClass(CreateCourseCommand.class);
        verify(service).create(command.capture(), any());
        org.assertj.core.api.Assertions.assertThat(
            command.getValue().creditBankEligible()).isFalse();
    }

    @Test
    void rejectsInvalidRequestAndUnknownProperty() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(globalAdmin());

        mockMvc.perform(post("/api/v1/courses")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId": "22222222-2222-2222-2222-222222222222",
                      "courseCode": " ",
                      "title": "과정",
                      "educationType": "ONLINE",
                      "startDate": "2026-09-01",
                      "endDate": "2026-09-01",
                      "capacity": 0
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/courses")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullCreateJson().replace(
                    "\"creditBankEligible\": true,",
                    "\"creditBankEligible\": null,")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/courses")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullCreateJson().replace(
                    "\"title\": \"과정 제목\",",
                    "\"title\": \"과정 제목\",\n  \"unknown\": true,")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(service, never()).create(any(), any());
    }

    @Test
    void detailTargetAudiences에_null원소가_있으면_422이다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(globalAdmin());

        mockMvc.perform(post("/api/v1/courses")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId": "22222222-2222-2222-2222-222222222222",
                      "courseCode": "COURSE-001",
                      "title": "과정 제목",
                      "educationType": "HYBRID",
                      "startDate": "2026-09-01",
                      "endDate": "2026-10-31",
                      "capacity": 30,
                      "detail": {
                        "targetAudiences": [null]
                      }
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(service, never()).create(any(), any());
    }

    @Test
    void getsCourseAndDoesNotExposeDeletedAt() throws Exception {
        String requestId = "7b706c0e-96cf-4d0a-b357-d80752facf1c";
        when(currentUserProvider.requireContext()).thenReturn(globalAdmin());
        when(service.get(eq(COURSE_ID), any())).thenReturn(
            new CourseView(course(), 0, "다배움평생교육원", "김강사"));

        mockMvc.perform(get("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, requestId))
            .andExpect(status().isOk())
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, requestId))
            .andExpect(jsonPath("$.data.id").value(COURSE_ID.toString()))
            .andExpect(jsonPath("$.data.title").value("과정 제목"))
            .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
            .andExpect(jsonPath("$.data.institutionName").value("다배움평생교육원"))
            .andExpect(jsonPath("$.data.mainInstructorName").value("김강사"))
            .andExpect(jsonPath("$.meta.requestId").value(requestId));
    }

    @Test
    void rejectsMalformedUuidAndServiceNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/courses/not-a-uuid")
                .with(user("reader").roles("USER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        when(service.get(eq(COURSE_ID), any())).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.COURSE_NOT_FOUND,
            "Course not found"
        ));
        mockMvc.perform(get("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("reader").roles("USER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    void mapsCourseAndInstitutionErrors() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(globalAdmin());
        reset(service);
        when(service.create(any(), any())).thenThrow(new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.COURSE_CONFLICT,
            "Course already exists"
        ));
        mockMvc.perform(post("/api/v1/courses")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullCreateJson()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COURSE_CONFLICT"));

        reset(service);
        when(service.create(any(), any())).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.INSTITUTION_NOT_FOUND,
            "Institution not found"
        ));
        mockMvc.perform(post("/api/v1/courses")
                .with(user("platform-admin").roles("PLATFORM_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullCreateJson()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("INSTITUTION_NOT_FOUND"));
    }

    @Test
    void requiresAuthenticationAndCourseManagerRole() throws Exception {
        // 상세 조회는 공개다. 쓰기는 계속 로그인이 필요하다.
        mockMvc.perform(post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullCreateJson()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/courses")
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullCreateJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void permitsSameInstitutionAdminAndPassesAuthenticatedContext() throws Exception {
        AuthenticatedUserContext context = new AuthenticatedUserContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID))
        );
        when(currentUserProvider.requireContext()).thenReturn(context);
        when(service.create(any(), any())).thenReturn(course());

        mockMvc.perform(post("/api/v1/courses")
                .with(user("institution-admin").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullCreateJson()))
            .andExpect(status().isCreated());

        verify(service).create(any(CreateCourseCommand.class), org.mockito.ArgumentMatchers.eq(context));
    }

    private String fullCreateJson() {
        return """
            {
              "institutionId": "22222222-2222-2222-2222-222222222222",
              "courseCode": "COURSE-001",
              "title": "과정 제목",
              "description": "상세 설명",
              "classificationCode": "TSIX_040201",
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
            COURSE_ID,
            INSTITUTION_ID,
            "COURSE-001",
            "과정 제목",
            "상세 설명",
            "TSIX_040201",
            CourseEducationType.HYBRID,
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 10, 31),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 25),
            30,
            "서울 교육장",
            "https://example.test/course",
            true,
            new BigDecimal("12.50"),
            CourseStatus.DRAFT,
            instant,
            instant,
            null
        );
    }

    private AuthenticatedUserContext globalAdmin() {
        return new AuthenticatedUserContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null))
        );
    }
}
