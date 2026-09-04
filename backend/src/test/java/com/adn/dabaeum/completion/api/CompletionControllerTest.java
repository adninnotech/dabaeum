package com.adn.dabaeum.completion.api;

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

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.completion.application.CompletionApplicationService;
import com.adn.dabaeum.completion.domain.Completion;
import com.adn.dabaeum.completion.domain.CompletionStatus;
import com.adn.dabaeum.testsupport.OpenApiContract;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
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

@WebMvcTest(CompletionController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=completion-task8-token")
@Import({
    CompletionApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CompletionControllerTest {

    private static final Path CONTRACT =
        Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID ENROLLMENT_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222");
    private static final UUID COMPLETION_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111");
    private static final String REQUEST_ID = "1f4b5d3f-1c29-48ae-94f6-4a66d9a1e2b1";

    @Autowired MockMvc mockMvc;
    @MockitoBean CompletionApplicationService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void getsEvaluatesAndConfirmsWithOpenApiEnvelope() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.get(ENROLLMENT_ID, manager())).thenReturn(completion(CompletionStatus.ELIGIBLE));
        when(service.evaluate(any(), eq(manager()))).thenReturn(completion(CompletionStatus.NOT_COMPLETED));
        when(service.confirm(ENROLLMENT_ID, manager())).thenReturn(completion(CompletionStatus.COMPLETED));

        mockMvc.perform(get("/api/v1/enrollments/{id}/completion", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ELIGIBLE"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        mockMvc.perform(post("/api/v1/enrollments/{id}/completion/evaluate", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("NOT_COMPLETED"))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        mockMvc.perform(post("/api/v1/enrollments/{id}/completion/confirm", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR));

        verify(service).evaluate(any(), eq(manager()));
        verify(service).confirm(ENROLLMENT_ID, manager());
    }

    @Test
    void rejectsUnknownPropertyAndLearnerCannotEvaluateOrConfirm() throws Exception {
        mockMvc.perform(post("/api/v1/enrollments/{id}/completion/evaluate", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson().replace("}", ",\"unexpected\":true}")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/enrollments/{id}/completion/evaluate", ENROLLMENT_ID)
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/enrollments/{id}/completion/confirm", ENROLLMENT_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }

    private String validJson() {
        return """
            {"attendanceRate":66.67,"completedMinutes":120,
             "creditValue":null,"failureReason":"출석 기준 미달"}
            """;
    }

    private Completion completion(CompletionStatus status) {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        return switch (status) {
            case ELIGIBLE -> new Completion(COMPLETION_ID, ENROLLMENT_ID, status,
                new BigDecimal("66.67"), 120, null, now, null, null, null, null, now, now);
            case NOT_COMPLETED -> new Completion(COMPLETION_ID, ENROLLMENT_ID, status,
                new BigDecimal("66.67"), 120, null, now, null, null, null, "출석 기준 미달", now, now);
            case COMPLETED -> new Completion(COMPLETION_ID, ENROLLMENT_ID, status,
                new BigDecimal("66.67"), 120, null, now, now, manager().userId(), now, null, now, now);
            default -> throw new IllegalArgumentException("unsupported test status");
        };
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(
            UUID.fromString("55555555-5555-5555-5555-555555555555"), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN",
                UUID.fromString("44444444-4444-4444-4444-444444444444"))));
    }
}
