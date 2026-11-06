package com.adn.dabaeum.enrollment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
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
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnrollmentController.class)
@AutoConfigureRestDocs(uriScheme = "https", uriHost = "api.dabaeum.local", uriPort = 443)
@Import({EnrollmentApiMapper.class, ApiExceptionHandler.class, ClockConfiguration.class,
    SecurityConfiguration.class, JacksonConfiguration.class, RequestIdFilter.class})
class EnrollmentCreateQueryDocumentationTest {

    private static final UUID COURSE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENROLLMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentApplicationService service;
    @MockitoBean com.adn.dabaeum.enrollment.application.EnrollmentBulkDecisionService bulkDecisionService;
    @MockitoBean com.adn.dabaeum.common.security.CurrentUserProvider currentUserProvider;

    @Test
    void documentsSelfProxyListAndGet() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(context("INSTITUTION_ADMIN"));
        when(service.createSelf(any(), any())).thenReturn(enrollment());
        mockMvc.perform(post("/api/v1/courses/{courseId}/enrollments", COURSE_ID)
                .with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + USER_ID + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/enrollments/" + ENROLLMENT_ID))
            .andDo(document("enrollment-create", preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()),
                requestFields(fieldWithPath("userId").description("대상 사용자"),
                    fieldWithPath("applicationType").type(JsonFieldType.STRING).optional().description("SELF")), responseFieldsFor(false)));

        when(service.createProxy(any(), any())).thenReturn(proxyEnrollment());
        mockMvc.perform(post("/api/v1/courses/{courseId}/proxy-enrollments", COURSE_ID)
                .with(user("admin").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + USER_ID + "\"}"))
            .andExpect(status().isCreated())
            .andDo(document("enrollment-proxy-create", preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()),
                requestFields(fieldWithPath("userId").description("대상 사용자")), responseFieldsFor(false)));

        when(service.list(any(), any())).thenReturn(new EnrollmentPage(
            List.of(new EnrollmentListItem(enrollment(),
                new EnrolleeProfile("시드학습자", null, null,
                    "seed.learner@dabaeum.kr", "010-1234-5678", null),
                null, 0, null)),
            0, 20, 1, 1));
        mockMvc.perform(get("/api/v1/courses/{courseId}/enrollments", COURSE_ID)
                .queryParam("page", "0").queryParam("size", "20")
                .queryParam("sort", "createdAt,desc")
                .with(user("admin").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andDo(document("enrollment-list", preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()),
                queryParameters(parameterWithName("page").description("페이지"),
                    parameterWithName("size").description("크기"), parameterWithName("sort").description("정렬")),
                responseFieldsFor(true)));

        when(service.get(any(), any())).thenReturn(enrollment());
        mockMvc.perform(get("/api/v1/enrollments/{enrollmentId}", ENROLLMENT_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andDo(document("enrollment-get", preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()),
                responseFieldsFor(false)));
    }

    private ResponseFieldsSnippet responseFieldsFor(boolean page) {
        String p = page ? "data[]" : "data";
        var fields = new java.util.ArrayList<org.springframework.restdocs.payload.FieldDescriptor>();
        fields.addAll(List.of(fieldWithPath(p + ".id").description("수강신청 ID"),
            fieldWithPath(p + ".courseId").description("과정 ID"), fieldWithPath(p + ".userId").description("사용자 ID"),
            fieldWithPath(p + ".appliedBy").type(JsonFieldType.STRING).optional().description("대리 신청자"),
            fieldWithPath(p + ".applicationType").description("신청 유형"), fieldWithPath(p + ".status").description("상태"),
            fieldWithPath(p + ".appliedAt").description("신청 시각"), fieldWithPath(p + ".approvedAt").type(JsonFieldType.STRING).optional().description("승인 시각"),
            fieldWithPath(p + ".rejectedAt").type(JsonFieldType.STRING).optional().description("거절 시각"), fieldWithPath(p + ".cancelledAt").type(JsonFieldType.STRING).optional().description("취소 시각"),
            fieldWithPath(p + ".withdrawnAt").type(JsonFieldType.STRING).optional().description("철회 시각"), fieldWithPath(p + ".rejectionReason").type(JsonFieldType.STRING).optional().description("거절 사유"),
            fieldWithPath(p + ".cancellationReason").type(JsonFieldType.STRING).optional().description("취소 사유"), fieldWithPath(p + ".createdAt").description("생성 시각"),
            fieldWithPath(p + ".updatedAt").description("수정 시각"),
            fieldWithPath(p + ".processedAt").type(JsonFieldType.STRING).optional().description("처리 시각"),
            fieldWithPath(p + ".waitlistPosition").type(JsonFieldType.NUMBER).optional().description("대기 순번"),
            fieldWithPath(p + ".progressPercent").type(JsonFieldType.NUMBER).description("출석 대비 진행률(%)"),
            fieldWithPath(p + ".lastStudiedAt").type(JsonFieldType.STRING).optional().description("마지막 출석 시각"),
            fieldWithPath(p + ".userName").type(JsonFieldType.STRING).optional().description("신청자 이름"),
            fieldWithPath(p + ".birthDate").type(JsonFieldType.STRING).optional().description("신청자 생년월일"),
            fieldWithPath(p + ".gender").type(JsonFieldType.STRING).optional().description("신청자 성별"),
            fieldWithPath(p + ".email").type(JsonFieldType.STRING).optional().description("신청자 이메일"),
            fieldWithPath(p + ".phone").type(JsonFieldType.STRING).optional().description("신청자 연락처"),
            fieldWithPath(p + ".profileImageId").type(JsonFieldType.STRING).optional().description("신청자 프로필 이미지"),
            fieldWithPath("meta.requestId").type(JsonFieldType.STRING).optional().description("요청 ID"),
            fieldWithPath("meta.timestamp").description("응답 시각")));
        if (page) {
            fields.addAll(List.of(fieldWithPath("page.page").description("페이지"), fieldWithPath("page.size").description("크기"),
                fieldWithPath("page.totalElements").description("전체 수"), fieldWithPath("page.totalPages").description("전체 페이지")));
        }
        return responseFields(fields.toArray(new org.springframework.restdocs.payload.FieldDescriptor[0]));
    }

    private AuthenticatedUserContext context(String role) {
        return new AuthenticatedUserContext(USER_ID, "LOCAL", Set.of(new AuthenticatedRole(role, INSTITUTION_ID)));
    }

    private Enrollment enrollment() {
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, USER_ID, null, EnrollmentApplicationType.SELF,
            EnrollmentStatus.APPLIED, now, null, null, null, null, null, null, now, now);
    }

    private Enrollment proxyEnrollment() {
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        return new Enrollment(ENROLLMENT_ID, COURSE_ID, USER_ID, USER_ID, EnrollmentApplicationType.ADMIN_PROXY,
            EnrollmentStatus.APPLIED, now, null, null, null, null, null, null, now, now);
    }
}
