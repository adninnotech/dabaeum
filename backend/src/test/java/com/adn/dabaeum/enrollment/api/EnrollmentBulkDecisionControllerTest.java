package com.adn.dabaeum.enrollment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.adn.dabaeum.enrollment.application.BulkDecisionResult;
import com.adn.dabaeum.enrollment.application.EnrollmentApplicationService;
import com.adn.dabaeum.enrollment.application.EnrollmentBulkDecisionService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 스펙 #49 목록 체크박스 일괄 승인·반려. 부분 성공은 200 본문으로 알린다. */
@WebMvcTest(EnrollmentController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=bulk-decision-token")
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
class EnrollmentBulkDecisionControllerTest {

    private static final UUID INSTITUTION_ID =
        UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MANAGER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ID_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ID_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentApplicationService service;
    @MockitoBean EnrollmentBulkDecisionService bulkDecisionService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 성공과_실패를_한_응답에_담는다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(bulkDecisionService.bulkDecide(any(), any())).thenReturn(new BulkDecisionResult(
            List.of(ID_A),
            List.of(new BulkDecisionResult.Failure(
                ID_B, "ENROLLMENT_STATUS_CONFLICT: Enrollment status has changed"))));

        mockMvc.perform(post("/api/v1/enrollments/bulk-decision")
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"enrollmentIds": ["%s", "%s"], "decision": "APPROVE"}
                    """.formatted(ID_A, ID_B)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.succeeded[0]").value(ID_A.toString()))
            .andExpect(jsonPath("$.data.failed[0].enrollmentId").value(ID_B.toString()))
            .andExpect(jsonPath("$.data.failed[0].reason").exists());
    }

    /** 계약이 maxItems 100 이므로 여기서는 계약 검증기를 걸지 않는다 — 요청 자체가 계약 위반이다. */
    @Test
    void 한_번에_100건을_넘으면_422다() throws Exception {
        String ids = IntStream.range(0, 101)
            .mapToObj(index -> "\"" + UUID.randomUUID() + "\"")
            .collect(Collectors.joining(","));

        mockMvc.perform(post("/api/v1/enrollments/bulk-decision")
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enrollmentIds\": [" + ids + "], \"decision\": \"APPROVE\"}"))
            .andExpect(status().isUnprocessableEntity());

        verify(bulkDecisionService, never()).bulkDecide(any(), any());
    }

    private AuthenticatedUserContext managerContext() {
        return new AuthenticatedUserContext(MANAGER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }
}
