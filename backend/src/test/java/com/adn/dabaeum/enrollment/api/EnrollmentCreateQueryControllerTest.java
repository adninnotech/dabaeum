package com.adn.dabaeum.enrollment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.enrollment.application.EnrollmentApplicationService;
import com.adn.dabaeum.enrollment.application.EnrollmentPage;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentListItem;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnrollmentController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=enrollment-task8-token")
@Import({
    EnrollmentApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class EnrollmentCreateQueryControllerTest {

    private static final UUID COURSE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENROLLMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String REQUEST_ID = "9b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentApplicationService service;
    @MockitoBean com.adn.dabaeum.enrollment.application.EnrollmentBulkDecisionService bulkDecisionService;
    @MockitoBean com.adn.dabaeum.common.security.CurrentUserProvider currentUserProvider;

    @Test
    void createsSelfAndProxyWithServerControlledFields() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learnerContext());
        when(service.createSelf(any(), any())).thenReturn(enrollment());
        mockMvc.perform(post("/api/v1/courses/{courseId}/enrollments", COURSE_ID)
                .with(user("learner").roles("LEARNER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + USER_ID + "\",\"applicationType\":\"SELF\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/enrollments/" + ENROLLMENT_ID))
            .andExpect(jsonPath("$.data.appliedBy").value(nullValue()));

        when(currentUserProvider.requireContext()).thenReturn(adminContext());
        when(service.createProxy(any(), any())).thenReturn(proxyEnrollment());
        mockMvc.perform(post("/api/v1/courses/{courseId}/proxy-enrollments", COURSE_ID)
                .with(user("admin").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + USER_ID + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/enrollments/" + ENROLLMENT_ID));
    }

    @Test
    void listsAndGetsEnrollmentWithRequestIdAndPageMeta() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(adminContext());
        when(service.list(any(), any())).thenReturn(new EnrollmentPage(
            List.of(new EnrollmentListItem(enrollment(),
                new EnrolleeProfile("시드학습자", null, null,
                    "seed.learner@dabaeum.kr", "010-1234-5678", null),
                null, 45, Instant.parse("2026-08-10T00:00:00Z"))),
            0, 20, 1, 1));
        mockMvc.perform(get("/api/v1/courses/{courseId}/enrollments", COURSE_ID)
                .with(user("admin").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(ENROLLMENT_ID.toString()))
            .andExpect(jsonPath("$.data[0].userName").value("시드학습자"))
            .andExpect(jsonPath("$.data[0].email").value("seed.learner@dabaeum.kr"))
            .andExpect(jsonPath("$.data[0].phone").value("010-1234-5678"))
            .andExpect(jsonPath("$.data[0].gender").doesNotExist())
            .andExpect(jsonPath("$.data[0].progressPercent").value(45))
            .andExpect(jsonPath("$.data[0].lastStudiedAt").value("2026-08-10T00:00:00Z"))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.meta.requestId").value(REQUEST_ID));

        when(service.get(eq(ENROLLMENT_ID), any())).thenReturn(enrollment());
        mockMvc.perform(get("/api/v1/enrollments/{enrollmentId}", ENROLLMENT_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPLIED"));
    }

    @Test
    void rejectsUnknownProxyFieldsAndUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/courses/{courseId}/proxy-enrollments", COURSE_ID)
                .with(user("admin").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + USER_ID + "\",\"appliedBy\":\"" + USER_ID + "\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/courses/{courseId}/enrollments", COURSE_ID))
            .andExpect(status().isUnauthorized());
        verify(service, never()).createProxy(any(), any());
    }

    private AuthenticatedUserContext learnerContext() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext adminContext() {
        return new AuthenticatedUserContext(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }

    private Enrollment enrollment() {
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, USER_ID, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPLIED, now, null, null,
            null, null, null, null, now, now);
    }

    private Enrollment proxyEnrollment() {
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, USER_ID,
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            EnrollmentApplicationType.ADMIN_PROXY, EnrollmentStatus.APPLIED, now, null, null,
            null, null, null, null, now, now);
    }
}
