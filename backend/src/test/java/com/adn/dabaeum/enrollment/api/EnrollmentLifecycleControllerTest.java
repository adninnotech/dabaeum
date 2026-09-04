package com.adn.dabaeum.enrollment.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.enrollment.application.EnrollmentApplicationService;
import com.adn.dabaeum.enrollment.application.RejectEnrollmentCommand;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnrollmentController.class)
@Import({EnrollmentApiMapper.class, ApiExceptionHandler.class, ClockConfiguration.class,
    SecurityConfiguration.class, JacksonConfiguration.class, RequestIdFilter.class})
class EnrollmentLifecycleControllerTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID ENROLLMENT_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555");
    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333");
    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentApplicationService service;
    @MockitoBean com.adn.dabaeum.common.security.CurrentUserProvider currentUserProvider;

    @Test
    void runsAllLifecycleTransitionsAndValidatesAgainstOpenApi() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(service.approve(eq(ENROLLMENT_ID), any())).thenReturn(enrollment(EnrollmentStatus.APPROVED));
        mockMvc.perform(post("/api/v1/enrollments/{id}/approve", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        when(service.reject(any(RejectEnrollmentCommand.class), any()))
            .thenReturn(enrollment(EnrollmentStatus.REJECTED));
        mockMvc.perform(post("/api/v1/enrollments/{id}/reject", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"요건 미충족\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        when(currentUserProvider.requireContext()).thenReturn(learnerContext());
        when(service.cancel(eq(ENROLLMENT_ID), any())).thenReturn(enrollment(EnrollmentStatus.CANCELLED));
        mockMvc.perform(post("/api/v1/enrollments/{id}/cancel", ENROLLMENT_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        when(service.withdraw(eq(ENROLLMENT_ID), any())).thenReturn(enrollment(EnrollmentStatus.WITHDRAWN));
        mockMvc.perform(post("/api/v1/enrollments/{id}/withdraw", ENROLLMENT_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));
    }

    @Test
    void rejectsInvalidBodiesAndBodiesOnBodylessTransitions() throws Exception {
        mockMvc.perform(post("/api/v1/enrollments/{id}/reject", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" \"}"))
            .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/v1/enrollments/{id}/reject", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"사유\",\"appliedBy\":\"" + USER_ID + "\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/enrollments/{id}/approve", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/enrollments/{id}/cancel", ENROLLMENT_ID)
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
        verify(service, never()).reject(any(), any());
    }

    @Test
    void requiresAuthenticationForLifecycleEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/enrollments/{id}/approve", ENROLLMENT_ID))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/enrollments/{id}/reject", ENROLLMENT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"사유\"}"))
            .andExpect(status().isForbidden());
    }

    private AuthenticatedUserContext learnerContext() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", INSTITUTION_ID)));
    }

    private AuthenticatedUserContext managerContext() {
        return new AuthenticatedUserContext(UUID.fromString(
            "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }

    private Enrollment enrollment(EnrollmentStatus status) {
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        Instant approvedAt = status == EnrollmentStatus.APPROVED
            || status == EnrollmentStatus.WITHDRAWN ? now : null;
        Instant rejectedAt = status == EnrollmentStatus.REJECTED ? now : null;
        Instant cancelledAt = status == EnrollmentStatus.CANCELLED ? now : null;
        Instant withdrawnAt = status == EnrollmentStatus.WITHDRAWN ? now : null;
        String rejectionReason = status == EnrollmentStatus.REJECTED ? "요건 미충족" : null;
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, USER_ID, null,
            EnrollmentApplicationType.SELF, status, now, approvedAt, rejectedAt, cancelledAt,
            withdrawnAt, rejectionReason, null, now, now);
    }
}
