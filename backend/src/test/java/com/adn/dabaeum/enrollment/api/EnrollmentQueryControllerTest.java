package com.adn.dabaeum.enrollment.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
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
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.application.EnrollmentQueryService;
import com.adn.dabaeum.enrollment.application.InstitutionEnrollmentPage;
import com.adn.dabaeum.enrollment.application.MyEnrollmentPage;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.enrollment.domain.InstitutionEnrollmentView;
import com.adn.dabaeum.enrollment.domain.MyEnrollmentView;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
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

@WebMvcTest(EnrollmentQueryController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=enrollment-query-token")
@Import({
    EnrollmentQueryApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class EnrollmentQueryControllerTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "50000000-0000-0000-0000-000000000005");
    private static final String REQUEST_ID = "1f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1";

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentQueryService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void listsMyEnrollmentsWithCourseAndInstitutionFields() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(service.listMyEnrollments(any(), any(), anyInt(), anyInt(), anyString()))
            .thenReturn(new MyEnrollmentPage(List.of(myEnrollment()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/users/me/enrollments")
                .with(user("learner").roles("LEARNER"))
                .param("page", "0").param("size", "20")
                .param("sort", "appliedAt,desc")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].courseTitle").value("AI 기초"))
            .andExpect(jsonPath("$.data[0].institutionName").value("다배움기관"))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
    }

    @Test
    void listsInstitutionEnrollmentsForInstitutionAdmin() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.listInstitutionEnrollments(
                any(), eq(INSTITUTION_ID), any(), anyInt(), anyInt(), anyString()))
            .thenReturn(new InstitutionEnrollmentPage(
                List.of(institutionEnrollment()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/institutions/{id}/enrollments", INSTITUTION_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .param("sort", "appliedAt,desc")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].userName").value("홍길동"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
    }

    @Test
    void rejectsInstitutionEnrollmentListWithoutManagerRole() throws Exception {
        mockMvc.perform(get("/api/v1/institutions/{id}/enrollments", INSTITUTION_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnknownStatusAndSort() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());

        mockMvc.perform(get("/api/v1/users/me/enrollments")
                .with(user("learner").roles("LEARNER"))
                .param("sort", "foo,desc"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/users/me/enrollments")
                .with(user("learner").roles("LEARNER"))
                .param("status", "NOPE"))
            .andExpect(status().isBadRequest());
    }

    private MyEnrollmentView myEnrollment() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new MyEnrollmentView(
            UUID.randomUUID(), UUID.randomUUID(), USER_ID,
            EnrollmentStatus.APPROVED, now, now,
            "AI 기초", "AI-101", CourseStatus.IN_PROGRESS, "다배움기관");
    }

    private InstitutionEnrollmentView institutionEnrollment() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new InstitutionEnrollmentView(
            UUID.randomUUID(), UUID.randomUUID(), USER_ID,
            EnrollmentStatus.APPLIED, now, now, "AI 기초", "홍길동");
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(UUID.fromString(
            "50000000-0000-0000-0000-000000000005"), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }
}
