package com.adn.dabaeum.badge.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.badge.application.IssueLearningBadgeCommand;
import com.adn.dabaeum.badge.application.LearningBadgeApplicationService;
import com.adn.dabaeum.badge.application.LearningBadgePage;
import com.adn.dabaeum.badge.application.ListUserBadgesQuery;
import com.adn.dabaeum.badge.domain.LearningBadge;
import com.adn.dabaeum.badge.domain.LearningBadgeStatus;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LearningBadgeController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=badge-task-token")
@Import({
    LearningBadgeApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class LearningBadgeControllerTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID BADGE_ID = UUID.fromString(
        "80000000-0000-0000-0000-000000000008");
    private static final UUID CREDENTIAL_ID = UUID.fromString(
        "70000000-0000-0000-0000-000000000007");
    private static final UUID COURSE_ID = UUID.fromString(
        "30000000-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000004");
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "50000000-0000-0000-0000-000000000005");
    private static final String REQUEST_ID = "1f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1";

    @Autowired MockMvc mockMvc;
    @MockitoBean LearningBadgeApplicationService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void issuesGetsAndListsBadgesWithOpenApiEnvelopes() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.issue(any(IssueLearningBadgeCommand.class))).thenReturn(issuedBadge());
        when(service.get(BADGE_ID, manager())).thenReturn(issuedBadge());
        when(service.listByUser(any(ListUserBadgesQuery.class))).thenReturn(
            new LearningBadgePage(List.of(issuedBadge()), 0, 20, 1, 1));

        mockMvc.perform(post("/api/v1/credentials/{id}/badges", CREDENTIAL_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Idempotency-Key", "badge-issue-1")
                .header("Authorization", "Bearer badge-task-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"badgeType\":\"COURSE_COMPLETION\",\"badgeName\":\"AI 기초 이수\"}")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/api/v1/badges/" + BADGE_ID))
            .andExpect(jsonPath("$.data.status").value("ISSUED"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        mockMvc.perform(get("/api/v1/badges/{id}", BADGE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Authorization", "Bearer badge-task-token")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(BADGE_ID.toString()))
            .andExpect(jsonPath("$.data.badgeName").value("AI 기초 이수"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        mockMvc.perform(get("/api/v1/users/{id}/badges", USER_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Authorization", "Bearer badge-task-token")
                .param("page", "0")
                .param("size", "20")
                .param("sort", "createdAt,desc")
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].badgeType").value("COURSE_COMPLETION"))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).issue(any(IssueLearningBadgeCommand.class));
    }

    @Test
    void rejectsBadgeIssueWithoutManagerRole() throws Exception {
        mockMvc.perform(post("/api/v1/credentials/{id}/badges", CREDENTIAL_ID)
                .with(user("learner").roles("LEARNER"))
                .header("Idempotency-Key", "badge-forbidden-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"badgeType\":\"COURSE_COMPLETION\",\"badgeName\":\"x\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingIdempotencyKeyAndInvalidBody() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());

        mockMvc.perform(post("/api/v1/credentials/{id}/badges", CREDENTIAL_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"badgeType\":\"T\",\"badgeName\":\"N\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/credentials/{id}/badges", CREDENTIAL_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header("Idempotency-Key", "badge-invalid-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"badgeType\":\"\",\"badgeName\":\"\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsUnknownBadgeListSortWithBadRequest() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());

        mockMvc.perform(get("/api/v1/users/{id}/badges", USER_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .param("sort", "foo,desc"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnauthenticatedBadgeRequests() throws Exception {
        mockMvc.perform(get("/api/v1/badges/{id}", BADGE_ID))
            .andExpect(status().isUnauthorized());
    }

    private LearningBadge issuedBadge() {
        Instant now = Instant.parse("2026-08-27T00:00:00Z");
        return new LearningBadge(BADGE_ID, USER_ID, COURSE_ID, CREDENTIAL_ID,
            "COURSE_COMPLETION", "AI 기초 이수", LearningBadgeStatus.ISSUED,
            null, now, null, now, now);
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(UUID.fromString(
            "50000000-0000-0000-0000-000000000005"), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }
}
