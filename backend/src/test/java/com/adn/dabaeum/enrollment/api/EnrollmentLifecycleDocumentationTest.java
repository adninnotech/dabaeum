package com.adn.dabaeum.enrollment.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnrollmentController.class)
@AutoConfigureRestDocs(uriScheme = "https", uriHost = "api.dabaeum.local", uriPort = 443)
@Import({EnrollmentApiMapper.class, ApiExceptionHandler.class, ClockConfiguration.class,
    SecurityConfiguration.class, JacksonConfiguration.class, RequestIdFilter.class})
class EnrollmentLifecycleDocumentationTest {

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
    void documentsApproveRejectCancelAndWithdraw() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(managerContext());
        when(service.approve(eq(ENROLLMENT_ID), any())).thenReturn(enrollment(EnrollmentStatus.APPROVED));
        mockMvc.perform(post("/api/v1/enrollments/{id}/approve", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("enrollment-approve", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()), responseFields(lifecycleResponseFields())));

        when(service.reject(any(RejectEnrollmentCommand.class), any()))
            .thenReturn(enrollment(EnrollmentStatus.REJECTED));
        mockMvc.perform(post("/api/v1/enrollments/{id}/reject", ENROLLMENT_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"요건 미충족\"}"))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("enrollment-reject", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                requestFields(fieldWithPath("reason").description("거절 사유")),
                responseFields(lifecycleResponseFields())));

        when(currentUserProvider.requireContext()).thenReturn(learnerContext());
        when(service.cancel(eq(ENROLLMENT_ID), any())).thenReturn(enrollment(EnrollmentStatus.CANCELLED));
        mockMvc.perform(post("/api/v1/enrollments/{id}/cancel", ENROLLMENT_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("enrollment-cancel", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()), responseFields(lifecycleResponseFields())));

        when(service.withdraw(eq(ENROLLMENT_ID), any())).thenReturn(enrollment(EnrollmentStatus.WITHDRAWN));
        mockMvc.perform(post("/api/v1/enrollments/{id}/withdraw", ENROLLMENT_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("enrollment-withdraw", preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()), responseFields(lifecycleResponseFields())));
    }

    private org.springframework.restdocs.payload.FieldDescriptor[] lifecycleResponseFields() {
        return new org.springframework.restdocs.payload.FieldDescriptor[] {
            fieldWithPath("data.id").description("수강신청 ID"),
            fieldWithPath("data.courseId").description("과정 ID"),
            fieldWithPath("data.userId").description("사용자 ID"),
            fieldWithPath("data.appliedBy").type(JsonFieldType.STRING).optional().description("대리 신청자"),
            fieldWithPath("data.applicationType").description("신청 유형"),
            fieldWithPath("data.status").description("상태"),
            fieldWithPath("data.appliedAt").description("신청 시각"),
            fieldWithPath("data.approvedAt").type(JsonFieldType.STRING).optional().description("승인 시각"),
            fieldWithPath("data.rejectedAt").type(JsonFieldType.STRING).optional().description("거절 시각"),
            fieldWithPath("data.cancelledAt").type(JsonFieldType.STRING).optional().description("취소 시각"),
            fieldWithPath("data.withdrawnAt").type(JsonFieldType.STRING).optional().description("철회 시각"),
            fieldWithPath("data.rejectionReason").type(JsonFieldType.STRING).optional().description("거절 사유"),
            fieldWithPath("data.cancellationReason").type(JsonFieldType.STRING).optional().description("취소 사유"),
            fieldWithPath("data.createdAt").description("생성 시각"),
            fieldWithPath("data.updatedAt").description("수정 시각"),
            fieldWithPath("meta.requestId").type(JsonFieldType.STRING).optional().description("요청 ID"),
            fieldWithPath("meta.timestamp").description("응답 시각")
        };
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
