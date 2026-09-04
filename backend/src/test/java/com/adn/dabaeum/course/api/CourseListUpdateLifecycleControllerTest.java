package com.adn.dabaeum.course.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.CourseApplicationService;
import com.adn.dabaeum.course.application.CoursePage;
import com.adn.dabaeum.course.application.ListCoursesQuery;
import com.adn.dabaeum.course.application.UpdateCourseCommand;
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
    "dabaeum.security.dev.bearer-token=course-task4-test-token")
@Import({
    CourseApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CourseListUpdateLifecycleControllerTest {

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
    void listsCoursesWithDefaultQueryAndPageMeta() throws Exception {
        when(service.list(any())).thenReturn(new CoursePage(
            List.of(course()),
            0,
            20,
            1,
            1
        ));

        mockMvc.perform(get("/api/v1/courses")
                .with(user("reader").roles("USER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(jsonPath("$.data[0].courseCode").value("COURSE-001"))
            .andExpect(jsonPath("$.page.page").value(0))
            .andExpect(jsonPath("$.page.size").value(20))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.page.totalPages").value(1))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).list(eq(new ListCoursesQuery(0, 20, "createdAt,desc")));
    }

    @Test
    void mapsPartialUpdateAndExplicitNullableClear() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(service.update(any(), any())).thenReturn(course());

        mockMvc.perform(put("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "수정 과정",
                      "description": null,
                      "status": "RECRUITMENT_CLOSED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(jsonPath("$.data.id").value(COURSE_ID.toString()))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        ArgumentCaptor<UpdateCourseCommand> command =
            ArgumentCaptor.forClass(UpdateCourseCommand.class);
        verify(service).update(command.capture(), eq(managerContext()));
        org.assertj.core.api.Assertions.assertThat(command.getValue().title())
            .matches(field -> field.present() && "수정 과정".equals(field.value()));
        org.assertj.core.api.Assertions.assertThat(command.getValue().description())
            .matches(field -> field.present() && field.value() == null);
        org.assertj.core.api.Assertions.assertThat(command.getValue().status())
            .matches(field -> field.present() && field.value() == CourseStatus.RECRUITMENT_CLOSED);
    }

    @Test
    void publishesAndClosesCourse() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(service.publish(COURSE_ID, managerContext())).thenReturn(course());
        when(service.close(COURSE_ID, managerContext())).thenReturn(course());

        mockMvc.perform(post("/api/v1/courses/{courseId}/publish", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(COURSE_ID.toString()))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        mockMvc.perform(post("/api/v1/courses/{courseId}/close", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(COURSE_ID.toString()))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).publish(COURSE_ID, managerContext());
        verify(service).close(COURSE_ID, managerContext());
    }

    @Test
    void mapsMalformedUuidAndApplicationErrors() throws Exception {
        mockMvc.perform(put("/api/v1/courses/not-a-uuid")
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(service.publish(COURSE_ID, managerContext())).thenThrow(new ApiException(
            HttpStatus.CONFLICT,
            ApiErrorCode.COURSE_STATUS_CONFLICT,
            "Invalid course status transition"
        ));
        mockMvc.perform(post("/api/v1/courses/{courseId}/publish", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("COURSE_STATUS_CONFLICT"));

        when(service.update(any(), any())).thenThrow(new ApiException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.COURSE_NOT_FOUND,
            "Course not found"
        ));
        mockMvc.perform(put("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    void mapsInvalidPageSizeAndSortToBadRequest() throws Exception {
        when(service.list(any())).thenThrow(new ApiException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.BAD_REQUEST,
            "Invalid query"
        ));

        mockMvc.perform(get("/api/v1/courses")
                .queryParam("page", "-1")
                .with(user("reader").roles("USER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(get("/api/v1/courses")
                .queryParam("size", "101")
                .with(user("reader").roles("USER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mockMvc.perform(get("/api/v1/courses")
                .queryParam("sort", "createdAt,sideways")
                .with(user("reader").roles("USER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void rejectsEmptyAndUnknownUpdateBodies() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(managerContext());

        mockMvc.perform(put("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(put("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"unknown\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(service, never()).update(any(), any());
    }

    @Test
    void requiresAuthenticationAndCourseManagerRole() throws Exception {
        mockMvc.perform(get("/api/v1/courses"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(put("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"수정\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/v1/courses/{courseId}/publish", COURSE_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private AuthenticatedUserContext managerContext() {
        return new AuthenticatedUserContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID))
        );
    }

    private Course course() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z");
        return new Course(
            COURSE_ID,
            INSTITUTION_ID,
            "COURSE-001",
            "과정 제목",
            "상세 설명",
            "개발",
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
            CourseStatus.RECRUITMENT_CLOSED,
            instant,
            instant,
            null
        );
    }
}
