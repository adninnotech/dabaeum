package com.adn.dabaeum.instructor.api;

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
import com.adn.dabaeum.instructor.application.ApplyInstructorCommand;
import com.adn.dabaeum.instructor.application.InstructorApplicationPage;
import com.adn.dabaeum.instructor.application.InstructorApplicationService;
import com.adn.dabaeum.instructor.application.InstructorApplicationView;
import com.adn.dabaeum.instructor.application.RejectInstructorApplicationCommand;
import com.adn.dabaeum.instructor.domain.InstructorApplicationStatus;
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

@WebMvcTest(InstructorApplicationController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=instructor-application-test-token")
@Import({
    InstructorApplicationApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class InstructorApplicationControllerTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID APPLICATION_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final String REQUEST_ID =
        "8b706c0e-96cf-4d0a-b357-d80752facf1c";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InstructorApplicationService service;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Test
    void learnerAppliesAndListsOwnApplications() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(service.apply(any(), any())).thenReturn(view(
            InstructorApplicationStatus.PENDING
        ));
        when(service.listMine(any(), any())).thenReturn(new InstructorApplicationPage(
            List.of(view(InstructorApplicationStatus.PENDING)), 0, 20, 1, 1
        ));

        mockMvc.perform(post(
                "/api/v1/institutions/{institutionId}/instructor-applications",
                INSTITUTION_ID
            )
                .with(user("learner").roles("LEARNER"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"applicationMessage":" 강사 경력 5년 "}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string(
                "Location",
                "http://localhost/api/v1/instructor-applications/" + APPLICATION_ID
            ))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.applicantEmail")
                .value("learner@example.com"));

        ArgumentCaptor<ApplyInstructorCommand> command =
            ArgumentCaptor.forClass(ApplyInstructorCommand.class);
        verify(service).apply(command.capture(), eq(learner()));
        org.assertj.core.api.Assertions.assertThat(command.getValue())
            .isEqualTo(new ApplyInstructorCommand(
                INSTITUTION_ID,
                " 강사 경력 5년 "
            ));

        mockMvc.perform(get("/api/v1/instructor-applications/me")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id")
                .value(APPLICATION_ID.toString()))
            .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void institutionAdminListsApprovesAndRejects() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.listInstitution(any(), any())).thenReturn(
            new InstructorApplicationPage(
                List.of(view(InstructorApplicationStatus.PENDING)),
                0, 20, 1, 1
            )
        );
        when(service.approve(eq(APPLICATION_ID), any())).thenReturn(
            view(InstructorApplicationStatus.APPROVED)
        );
        when(service.reject(any(), any())).thenReturn(
            view(InstructorApplicationStatus.REJECTED)
        );

        mockMvc.perform(get(
                "/api/v1/institutions/{institutionId}/instructor-applications",
                INSTITUTION_ID
            ).with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].institutionId")
                .value(INSTITUTION_ID.toString()));

        mockMvc.perform(post(
                "/api/v1/instructor-applications/{applicationId}/approve",
                APPLICATION_ID
            ).with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(post(
                "/api/v1/instructor-applications/{applicationId}/reject",
                APPLICATION_ID
            )
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rejectionReason":"경력 증빙 필요"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        ArgumentCaptor<RejectInstructorApplicationCommand> command =
            ArgumentCaptor.forClass(RejectInstructorApplicationCommand.class);
        verify(service).reject(command.capture(), eq(manager()));
        org.assertj.core.api.Assertions.assertThat(command.getValue())
            .isEqualTo(new RejectInstructorApplicationCommand(
                APPLICATION_ID,
                "경력 증빙 필요"
            ));
    }

    @Test
    void rejectsInvalidBodyAndUnauthorizedAccess() throws Exception {
        mockMvc.perform(post(
                "/api/v1/institutions/{institutionId}/instructor-applications",
                INSTITUTION_ID
            )
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationMessage\":\"" + "가".repeat(1001) + "\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.details[0]").value("applicationMessage"));

        mockMvc.perform(get("/api/v1/instructor-applications/me"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post(
                "/api/v1/instructor-applications/{applicationId}/approve",
                APPLICATION_ID
            ).with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }

    private InstructorApplicationView view(InstructorApplicationStatus status) {
        Instant now = Instant.parse("2026-08-12T00:00:00Z");
        return new InstructorApplicationView(
            APPLICATION_ID, USER_ID, "학습자", "learner@example.com",
            "010-0000-0000", INSTITUTION_ID, status, "강사 경력 5년",
            status == InstructorApplicationStatus.REJECTED ? "경력 증빙 필요" : null,
            status == InstructorApplicationStatus.PENDING ? null : USER_ID,
            now, status == InstructorApplicationStatus.PENDING ? null : now,
            now, now
        );
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(
            USER_ID, "LOCAL", Set.of(new AuthenticatedRole("LEARNER", null))
        );
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(
            USER_ID,
            "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID))
        );
    }
}
