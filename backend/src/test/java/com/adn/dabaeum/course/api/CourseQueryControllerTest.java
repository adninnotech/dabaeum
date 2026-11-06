package com.adn.dabaeum.course.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.adn.dabaeum.course.application.CoursePage;
import com.adn.dabaeum.course.application.CourseQueryService;
import com.adn.dabaeum.course.application.InstitutionInstructorPage;
import com.adn.dabaeum.course.application.InstructorCoursePage;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseView;
import com.adn.dabaeum.course.domain.InstitutionInstructorView;
import com.adn.dabaeum.course.domain.InstructorCourseStats;
import com.adn.dabaeum.course.domain.InstructorCourseView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseQueryController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=course-query-token")
@Import({
    CourseQueryApiMapper.class,
    CourseApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CourseQueryControllerTest {

    private static final UUID INSTRUCTOR_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "50000000-0000-0000-0000-000000000005");
    private static final String REQUEST_ID = "1f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1";

    @Autowired MockMvc mockMvc;
    @MockitoBean CourseQueryService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void listsInstructorCoursesWithRoleAndEnrolledCount() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(service.listInstructorCourses(any(), any(), anyInt(), anyInt(), anyString()))
            .thenReturn(new InstructorCoursePage(
                List.of(new InstructorCourseView(
                    course(), CourseInstructorRole.MAIN, 7, "다배움평생교육원", "김강사")),
                0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/instructors/me/courses")
                .with(user("instructor").roles("INSTRUCTOR"))
                .param("sort", "createdAt,desc")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].instructorRole").value("MAIN"))
            .andExpect(jsonPath("$.data[0].enrolledCount").value(7))
            .andExpect(jsonPath("$.data[0].title").value("AI 기초"))
            .andExpect(jsonPath("$.data[0].institutionName").value("다배움평생교육원"))
            .andExpect(jsonPath("$.data[0].mainInstructorName").value("김강사"));
    }

    @Test
    void returnsInstructorCourseStats() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(service.instructorCourseStats(any()))
            .thenReturn(new InstructorCourseStats(4, 1, 2, 1, 7, 3));

        mockMvc.perform(get("/api/v1/instructors/me/courses/stats")
                .with(user("instructor").roles("INSTRUCTOR"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(4))
            .andExpect(jsonPath("$.data.inProgress").value(2))
            .andExpect(jsonPath("$.data.studentCount").value(7))
            .andExpect(jsonPath("$.data.inquiryCount").value(3));
    }

    @Test
    void listsInstitutionCoursesAndInstructors() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.listInstitutionCourses(
                any(), eq(INSTITUTION_ID), any(), anyInt(), anyInt(), anyString()))
            .thenReturn(new CoursePage(
                List.of(new CourseView(course(), 3, "다배움평생교육원", "김강사")), 0, 20, 1, 1));
        when(service.listInstitutionInstructors(
                any(), eq(INSTITUTION_ID), any(), any(), anyInt(), anyInt(), anyString()))
            .thenReturn(new InstitutionInstructorPage(
                List.of(instructorView()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/institutions/{id}/courses", INSTITUTION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].title").value("AI 기초"))
            .andExpect(jsonPath("$.data[0].institutionName").value("다배움평생교육원"))
            .andExpect(jsonPath("$.data[0].mainInstructorName").value("김강사"));

        mockMvc.perform(get("/api/v1/institutions/{id}/instructors", INSTITUTION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .param("sort", "joinedAt,desc")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("김강사"))
            .andExpect(jsonPath("$.data[0].courseCount").value(3))
            .andExpect(jsonPath("$.data[0].lastLoginAt").value("2026-09-01T00:00:00Z"))
            .andExpect(jsonPath("$.data[0].birthDate").value("1990-05-20"));
    }

    @Test
    void rejectsInstitutionQueriesWithoutManagerRole() throws Exception {
        mockMvc.perform(get("/api/v1/institutions/{id}/courses", INSTITUTION_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/instructors/me/courses")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }

    private Course course() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new Course(
            UUID.randomUUID(), INSTITUTION_ID, "AI-101", "AI 기초",
            null, null, CourseEducationType.OFFLINE,
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-12-01"),
            null, null, 30, null, null, false, null,
            CourseStatus.IN_PROGRESS, now, now, null);
    }

    private InstitutionInstructorView instructorView() {
        return new InstitutionInstructorView(
            UUID.randomUUID(), "김강사", "instructor@example.com", null,
            "ACTIVE", Instant.parse("2026-08-01T00:00:00Z"), 3, null,
            Instant.parse("2026-09-01T00:00:00Z"), LocalDate.of(1990, 5, 20));
    }

    private AuthenticatedUserContext instructor() {
        return new AuthenticatedUserContext(INSTRUCTOR_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(UUID.fromString(
            "50000000-0000-0000-0000-000000000005"), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }
}
