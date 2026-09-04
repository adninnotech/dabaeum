package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.common.api.FrontendApiGuideController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigurationTest.ProbeController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=unit-test-token")
@Import({
    SecurityConfiguration.class,
    ClockConfiguration.class,
    DevBearerSecurityConfiguration.class,
    RequestIdFilter.class,
    FrontendApiGuideController.class,
    SecurityConfigurationTest.ProbeController.class
})
class SecurityConfigurationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void frontendApiGuideIsPublicWithoutChangingProtectedApiRules()
        throws Exception {
        mockMvc.perform(get("/api-guide/index.html"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api-guide/dabaeum-frontend-api-flow.md"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/institutions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void pingIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/system/ping"))
            .andExpect(status().isOk());
    }

    @Test
    void institutionGetWithoutAuthenticationReturnsContractUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/institutions"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void institutionPostWithRegularUserReturnsContractForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/institutions")
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void institutionPostWithPlatformAdminPassesSecurity() throws Exception {
        mockMvc.perform(post("/api/v1/institutions")
                .with(user("local-platform-admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void coursePostWithLearnerReturnsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/courses")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void coursePostWithInstitutionAdminPassesSecurity() throws Exception {
        mockMvc.perform(post("/api/v1/courses")
                .with(user("institution-admin").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void courseGetWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/courses/22222222-2222-2222-2222-222222222222"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void courseListWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/courses"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void courseLifecycleMutationsRejectLearnerAndAllowManager() throws Exception {
        mockMvc.perform(put("/api/v1/courses/{courseId}",
                "22222222-2222-2222-2222-222222222222")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/courses/{courseId}/publish",
                "22222222-2222-2222-2222-222222222222")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/courses/{courseId}/close",
                "22222222-2222-2222-2222-222222222222")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/courses/{courseId}",
                "22222222-2222-2222-2222-222222222222")
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/courses/{courseId}/publish",
                "22222222-2222-2222-2222-222222222222")
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/courses/{courseId}/close",
                "22222222-2222-2222-2222-222222222222")
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void courseSessionReadRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/courses/{courseId}/sessions",
                "22222222-2222-2222-2222-222222222222"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/sessions/{sessionId}",
                "22222222-2222-2222-2222-222222222222"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void courseSessionWriteAllowsCandidatesAndRejectsLearner() throws Exception {
        mockMvc.perform(post("/api/v1/courses/{courseId}/sessions",
                "22222222-2222-2222-2222-222222222222")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/sessions/{sessionId}",
                "22222222-2222-2222-2222-222222222222")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/courses/{courseId}/sessions",
                "22222222-2222-2222-2222-222222222222")
                .with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/sessions/{sessionId}",
                "22222222-2222-2222-2222-222222222222")
                .with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isOk());
    }

    @Test
    void attendanceQrIssuanceAllowsManagersAndRejectsLearner() throws Exception {
        String sessionId = "22222222-2222-2222-2222-222222222222";
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/qr-token", sessionId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/qr-token", sessionId)
                .with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/qr-token", sessionId)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void attendanceRecordAllowsAuthenticatedLearnerAndRejectsAnonymousOrRegularUser() throws Exception {
        String sessionId = "22222222-2222-2222-2222-222222222222";
        String attendanceId = "33333333-3333-3333-3333-333333333333";
        mockMvc.perform(get("/api/v1/sessions/{sessionId}/attendance", sessionId))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/attendance", sessionId)
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sessions/{sessionId}/attendance", sessionId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sessions/{sessionId}/attendance", sessionId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/attendance/{attendanceId}", attendanceId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
    }

    @Test
    void completionReadRequiresAuthenticationAndMutationsRequireManagerRole() throws Exception {
        String enrollmentId = "22222222-2222-2222-2222-222222222222";
        mockMvc.perform(get("/api/v1/enrollments/{id}/completion", enrollmentId))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/enrollments/{id}/completion/evaluate", enrollmentId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/enrollments/{id}/completion/confirm", enrollmentId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/enrollments/{id}/completion/evaluate", enrollmentId)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/enrollments/{id}/completion/confirm", enrollmentId)
                .with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isOk());
    }

    @Test
    void credentialRoutesApplyAuthenticationAndIssueRoleBoundaries() throws Exception {
        String completionId = "22222222-2222-2222-2222-222222222222";
        String credentialId = "33333333-3333-3333-3333-333333333333";
        String userId = "44444444-4444-4444-4444-444444444444";

        mockMvc.perform(get("/api/v1/credentials/{credentialId}", credentialId))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/{userId}/credentials", userId))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/completions/{completionId}/credentials", completionId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/completions/{completionId}/credentials", completionId)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/credentials/{credentialId}", credentialId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
    }

    @Test
    void enrollmentRoutesApplyRoleBoundaries() throws Exception {
        String courseId = "22222222-2222-2222-2222-222222222222";
        String enrollmentId = "33333333-3333-3333-3333-333333333333";

        mockMvc.perform(get("/api/v1/courses/{courseId}/enrollments", courseId))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/enrollments/{enrollmentId}", enrollmentId))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/courses/{courseId}/enrollments", courseId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/courses/{courseId}/proxy-enrollments", courseId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/courses/{courseId}/proxy-enrollments", courseId)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses/{courseId}/enrollments", courseId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/enrollments/{enrollmentId}", enrollmentId)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
    }

    @Test
    void userAdminPathsRejectRegularUser() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users")
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/{userId}",
                "22222222-2222-2222-2222-222222222222")
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/{userId}/status",
                "22222222-2222-2222-2222-222222222222")
                .with(user("regular-user").roles("USER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void userMeWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void enrollmentLifecycleSecuritySeparatesDecisionAndSubjectRoles() throws Exception {
        String id = "33333333-3333-3333-3333-333333333333";
        mockMvc.perform(post("/api/v1/enrollments/{id}/approve", id)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/enrollments/{id}/approve", id)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/enrollments/{id}/reject", id)
                .with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/enrollments/{id}/cancel", id)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/enrollments/{id}/withdraw", id)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/system/ping")
        ResponseEntity<String> ping() {
            return ResponseEntity.ok("OK");
        }

        @GetMapping("/api/v1/private-probe")
        ResponseEntity<String> privateProbe() {
            return ResponseEntity.ok("PRIVATE");
        }

        @GetMapping("/api/v1/institutions")
        ResponseEntity<String> institutions() {
            return ResponseEntity.ok("INSTITUTIONS");
        }

        @PostMapping("/api/v1/institutions")
        ResponseEntity<String> createInstitution() {
            return ResponseEntity.ok("CREATED");
        }

        @PostMapping("/api/v1/courses")
        ResponseEntity<String> createCourse() {
            return ResponseEntity.ok("COURSE_CREATED");
        }

        @GetMapping("/api/v1/courses/{courseId}")
        ResponseEntity<String> course() {
            return ResponseEntity.ok("COURSE");
        }

        @GetMapping("/api/v1/courses/{courseId}/sessions")
        ResponseEntity<String> courseSessions() {
            return ResponseEntity.ok("COURSE_SESSIONS");
        }

        @PostMapping("/api/v1/courses/{courseId}/sessions")
        ResponseEntity<String> createCourseSession() {
            return ResponseEntity.ok("COURSE_SESSION_CREATED");
        }

        @GetMapping("/api/v1/courses/{courseId}/enrollments")
        ResponseEntity<String> courseEnrollments() {
            return ResponseEntity.ok("COURSE_ENROLLMENTS");
        }

        @PostMapping("/api/v1/courses/{courseId}/enrollments")
        ResponseEntity<String> createEnrollment() {
            return ResponseEntity.ok("ENROLLMENT_CREATED");
        }

        @PostMapping("/api/v1/courses/{courseId}/proxy-enrollments")
        ResponseEntity<String> createProxyEnrollment() {
            return ResponseEntity.ok("PROXY_ENROLLMENT_CREATED");
        }

        @GetMapping("/api/v1/enrollments/{enrollmentId}")
        ResponseEntity<String> enrollment() {
            return ResponseEntity.ok("ENROLLMENT");
        }

        @GetMapping("/api/v1/enrollments/{enrollmentId}/completion")
        ResponseEntity<String> completion() {
            return ResponseEntity.ok("COMPLETION");
        }

        @PostMapping("/api/v1/enrollments/{enrollmentId}/completion/evaluate")
        ResponseEntity<String> evaluateCompletion() {
            return ResponseEntity.ok("COMPLETION_EVALUATED");
        }

        @PostMapping("/api/v1/enrollments/{enrollmentId}/completion/confirm")
        ResponseEntity<String> confirmCompletion() {
            return ResponseEntity.ok("COMPLETION_CONFIRMED");
        }

        @GetMapping("/api/v1/sessions/{sessionId}")
        ResponseEntity<String> session() {
            return ResponseEntity.ok("SESSION");
        }

        @PostMapping("/api/v1/sessions/{sessionId}/qr-token")
        ResponseEntity<String> issueAttendanceQrToken() {
            return ResponseEntity.ok("QR_TOKEN");
        }

        @GetMapping("/api/v1/sessions/{sessionId}/attendance")
        ResponseEntity<String> listAttendance() {
            return ResponseEntity.ok("ATTENDANCE_LIST");
        }

        @PostMapping("/api/v1/sessions/{sessionId}/attendance")
        ResponseEntity<String> recordAttendance() {
            return ResponseEntity.ok("ATTENDANCE_CREATED");
        }

        @GetMapping("/api/v1/attendance/{attendanceId}")
        ResponseEntity<String> attendance() {
            return ResponseEntity.ok("ATTENDANCE");
        }

        @PutMapping("/api/v1/sessions/{sessionId}")
        ResponseEntity<String> updateSession() {
            return ResponseEntity.ok("SESSION_UPDATED");
        }

        @GetMapping("/api/v1/courses")
        ResponseEntity<String> courseList() {
            return ResponseEntity.ok("COURSES");
        }

        @PutMapping("/api/v1/courses/{courseId}")
        ResponseEntity<String> updateCourse() {
            return ResponseEntity.ok("COURSE_UPDATED");
        }

        @PostMapping("/api/v1/courses/{courseId}/publish")
        ResponseEntity<String> publishCourse() {
            return ResponseEntity.ok("COURSE_PUBLISHED");
        }

        @PostMapping("/api/v1/courses/{courseId}/close")
        ResponseEntity<String> closeCourse() {
            return ResponseEntity.ok("COURSE_CLOSED");
        }

        @PostMapping("/api/v1/users")
        ResponseEntity<String> createUser() {
            return ResponseEntity.ok("USER_CREATED");
        }

        @GetMapping("/api/v1/users")
        ResponseEntity<String> users() {
            return ResponseEntity.ok("USERS");
        }

        @GetMapping("/api/v1/users/{userId}")
        ResponseEntity<String> user() {
            return ResponseEntity.ok("USER");
        }

        @org.springframework.web.bind.annotation.PatchMapping(
            "/api/v1/users/{userId}/status"
        )
        ResponseEntity<String> userStatus() {
            return ResponseEntity.ok("USER_STATUS");
        }

        @GetMapping("/api/v1/users/me")
        ResponseEntity<String> currentUser() {
            return ResponseEntity.ok("CURRENT_USER");
        }

        @PostMapping("/api/v1/enrollments/{enrollmentId}/approve")
        ResponseEntity<String> approveEnrollment() {
            return ResponseEntity.ok("APPROVED");
        }

        @PostMapping("/api/v1/enrollments/{enrollmentId}/reject")
        ResponseEntity<String> rejectEnrollment() {
            return ResponseEntity.ok("REJECTED");
        }

        @PostMapping("/api/v1/enrollments/{enrollmentId}/cancel")
        ResponseEntity<String> cancelEnrollment() {
            return ResponseEntity.ok("CANCELLED");
        }

        @PostMapping("/api/v1/enrollments/{enrollmentId}/withdraw")
        ResponseEntity<String> withdrawEnrollment() {
            return ResponseEntity.ok("WITHDRAWN");
        }

        @PostMapping("/api/v1/completions/{completionId}/credentials")
        ResponseEntity<String> issueCredential() {
            return ResponseEntity.ok("CREDENTIAL_ACCEPTED");
        }

        @GetMapping("/api/v1/credentials/{credentialId}")
        ResponseEntity<String> credential() {
            return ResponseEntity.ok("CREDENTIAL");
        }

        @GetMapping("/api/v1/users/{userId}/credentials")
        ResponseEntity<String> userCredentials() {
            return ResponseEntity.ok("USER_CREDENTIALS");
        }
    }
}
