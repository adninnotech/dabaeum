package com.adn.dabaeum.course.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.application.AssignCourseInstructorCommand;
import com.adn.dabaeum.course.application.CourseInstructorApplicationService;
import com.adn.dabaeum.course.application.CourseInstructorView;
import com.adn.dabaeum.course.application.UpdateCourseInstructorCommand;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseInstructorController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=course-instructor-test-token")
@Import({
    CourseInstructorApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CourseInstructorControllerTest {

    private static final UUID COURSE_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID INSTRUCTOR_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID ASSIGNMENT_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "44444444-4444-4444-4444-444444444444"
    );

    @Autowired MockMvc mockMvc;
    @MockitoBean CourseInstructorApplicationService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void authenticatedUserListsAndAdminManagesCourseInstructors() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.list(COURSE_ID)).thenReturn(List.of(view(
            CourseInstructorRole.MAIN
        )));
        when(service.assign(any(), any())).thenReturn(view(
            CourseInstructorRole.MAIN
        ));
        when(service.updateRole(any(), any())).thenReturn(view(
            CourseInstructorRole.ASSISTANT
        ));
        when(service.remove(eq(COURSE_ID), eq(INSTRUCTOR_ID), any()))
            .thenReturn(view(CourseInstructorRole.ASSISTANT));

        mockMvc.perform(get("/api/v1/courses/{courseId}/instructors", COURSE_ID)
                .with(user("reader").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].instructorName").value("강사"))
            .andExpect(jsonPath("$.data[0].instructorPhone").doesNotExist());

        mockMvc.perform(post("/api/v1/courses/{courseId}/instructors", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"userId":"22222222-2222-2222-2222-222222222222","role":"MAIN"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                "http://localhost/api/v1/courses/" + COURSE_ID
                    + "/instructors/" + INSTRUCTOR_ID
            ));

        mockMvc.perform(put(
                "/api/v1/courses/{courseId}/instructors/{userId}",
                COURSE_ID,
                INSTRUCTOR_ID
            )
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ASSISTANT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("ASSISTANT"));

        mockMvc.perform(delete(
                "/api/v1/courses/{courseId}/instructors/{userId}",
                COURSE_ID,
                INSTRUCTOR_ID
            ).with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());

        ArgumentCaptor<AssignCourseInstructorCommand> assign =
            ArgumentCaptor.forClass(AssignCourseInstructorCommand.class);
        verify(service).assign(assign.capture(), eq(manager()));
        org.assertj.core.api.Assertions.assertThat(assign.getValue().role())
            .isEqualTo(CourseInstructorRole.MAIN);
        ArgumentCaptor<UpdateCourseInstructorCommand> update =
            ArgumentCaptor.forClass(UpdateCourseInstructorCommand.class);
        verify(service).updateRole(update.capture(), eq(manager()));
        org.assertj.core.api.Assertions.assertThat(update.getValue().role())
            .isEqualTo(CourseInstructorRole.ASSISTANT);
    }

    @Test
    void blocksLearnerManagementAndRequiresAuthenticationForList() throws Exception {
        mockMvc.perform(get("/api/v1/courses/{courseId}/instructors", COURSE_ID))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/courses/{courseId}/instructors", COURSE_ID)
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"userId":"22222222-2222-2222-2222-222222222222","role":"MAIN"}
                    """))
            .andExpect(status().isForbidden());
    }

    private CourseInstructorView view(CourseInstructorRole role) {
        Instant now = Instant.parse("2026-08-12T00:00:00Z");
        return new CourseInstructorView(
            ASSIGNMENT_ID, COURSE_ID, INSTRUCTOR_ID, "강사",
            "instructor@example.com", role, now, now
        );
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(
            UUID.fromString("55555555-5555-5555-5555-555555555555"),
            "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID))
        );
    }
}
