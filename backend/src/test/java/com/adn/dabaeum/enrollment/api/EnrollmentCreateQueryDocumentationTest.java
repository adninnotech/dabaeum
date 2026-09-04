package com.adn.dabaeum.enrollment.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
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
import org.springframework.restdocs.payload.ResponseFieldsSnippet;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnrollmentController.class)
@AutoConfigureRestDocs(uriScheme = "https", uriHost = "api.dabaeum.local", uriPort = 443)
@Import({EnrollmentApiMapper.class, ApiExceptionHandler.class, ClockConfiguration.class,
    SecurityConfiguration.class, JacksonConfiguration.class, RequestIdFilter.class})
class EnrollmentCreateQueryDocumentationTest {

    private static final Path CONTRACT = Path.of("docs/api/dabaeum-api-v1.yaml").toAbsolutePath();
    private static final UUID COURSE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENROLLMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentApplicationService service;
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
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("enrollment-create", preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()),
                requestFields(fieldWithPath("userId").description("대상 사용자"),
                    fieldWithPath("applicationType").type(JsonFieldType.STRING).optional().description("SELF")), responseFieldsFor(false)));

        when(service.createProxy(any(), any())).thenReturn(proxyEnrollment());
        mockMvc.perform(post("/api/v1/courses/{courseId}/proxy-enrollments", COURSE_ID)
                .with(user("admin").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + USER_ID + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("enrollment-proxy-create", preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()),
                requestFields(fieldWithPath("userId").description("대상 사용자")), responseFieldsFor(false)));

        when(service.list(any(), any())).thenReturn(new EnrollmentPage(List.of(enrollment()), 0, 20, 1, 1));
        mockMvc.perform(get("/api/v1/courses/{courseId}/enrollments", COURSE_ID)
                .queryParam("page", "0").queryParam("size", "20")
                .queryParam("sort", "createdAt,desc")
                .with(user("admin").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
            .andDo(document("enrollment-list", preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()),
                queryParameters(parameterWithName("page").description("페이지"),
                    parameterWithName("size").description("크기"), parameterWithName("sort").description("정렬")),
                responseFieldsFor(true)));

        when(service.get(any(), any())).thenReturn(enrollment());
        mockMvc.perform(get("/api/v1/enrollments/{enrollmentId}", ENROLLMENT_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OpenApiContract.VALIDATOR))
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
            fieldWithPath(p + ".updatedAt").description("수정 시각"), fieldWithPath("meta.requestId").type(JsonFieldType.STRING).optional().description("요청 ID"),
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
