package com.adn.dabaeum.inquiry.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
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
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.inquiry.application.InquiryApplicationService;
import com.adn.dabaeum.inquiry.application.InquiryViewPage;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
import com.adn.dabaeum.inquiry.domain.InquiryView;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관리자용 강사별 문의 내역: {@code /instructors/{userId}/inquiries}.
 *
 * <p>응답·필터는 {@code /instructors/me/inquiries} 와 같다 — (d) 는 두 경로가 같은 결과를
 * 준다는 것을 같은 서비스 위임으로 확인한다.
 */
@WebMvcTest(InquiryController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=instructor-inquiry-by-user-token")
@Import({
    InquiryApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class InstructorInquiryByUserControllerTest {

    private static final UUID COURSE_ID = UUID.fromString("90000000-0000-0000-0000-000000000009");
    private static final UUID INSTRUCTOR_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");

    @Autowired MockMvc mockMvc;
    @MockitoBean InquiryApplicationService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 관리자가_강사의_문의를_userId_경로로_본다() throws Exception {
        InquiryViewPage page = page();
        when(service.listForInstructorOf(eq(INSTRUCTOR_ID), any(), any(), anyInt(), anyInt()))
            .thenReturn(page);

        mockMvc.perform(get("/api/v1/instructors/{userId}/inquiries", INSTRUCTOR_ID)
                .param("courseId", COURSE_ID.toString())
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].courseTitle").value("AI 기초"));

        verify(service).listForInstructorOf(
            eq(INSTRUCTOR_ID), isNull(), eq(COURSE_ID), anyInt(), anyInt());
    }

    @Test
    void me_경로와_userId_경로가_같은_강사에_대해_같은_결과를_준다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        InquiryViewPage page = page();
        when(service.listForInstructor(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(page);
        when(service.listForInstructorOf(eq(INSTRUCTOR_ID), any(), any(), anyInt(), anyInt()))
            .thenReturn(page);

        mockMvc.perform(get("/api/v1/instructors/me/inquiries")
                .with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].courseTitle").value("AI 기초"))
            .andExpect(jsonPath("$.data[0].status").value("PENDING"))
            .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/api/v1/instructors/{userId}/inquiries", INSTRUCTOR_ID)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].courseTitle").value("AI 기초"))
            .andExpect(jsonPath("$.data[0].status").value("PENDING"))
            .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void 없는_강사는_404다() throws Exception {
        when(service.listForInstructorOf(any(), any(), any(), anyInt(), anyInt()))
            .thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, ApiErrorCode.USER_NOT_FOUND, "User not found"));

        mockMvc.perform(get("/api/v1/instructors/{userId}/inquiries", INSTRUCTOR_ID)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isNotFound());
    }

    @Test
    void 학습자는_남의_강사_문의를_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/instructors/{userId}/inquiries", INSTRUCTOR_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void 강사도_남의_강사_문의를_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/instructors/{userId}/inquiries", INSTRUCTOR_ID)
                .with(user("instructor").roles("INSTRUCTOR")))
            .andExpect(status().isForbidden());
    }

    private InquiryViewPage page() {
        return new InquiryViewPage(List.of(inquiry()), 0, 20, 1, 1);
    }

    private InquiryView inquiry() {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        EnrolleeProfile profile = new EnrolleeProfile(
            "시드학습자", null, null, "seed.learner@dabaeum.kr", "010-1234-5678", null);
        return new InquiryView(
            UUID.randomUUID(), INSTRUCTOR_ID, COURSE_ID, "문의 제목", InquiryStatus.PENDING,
            InquiryType.COURSE, now, "AI 기초", profile, "다배움기관", "강사", "TSIX_010201", "OFFLINE", 5,
            "태그", null, null, null);
    }

    private AuthenticatedUserContext instructor() {
        return new AuthenticatedUserContext(
            INSTRUCTOR_ID, "LOCAL", Set.of(new AuthenticatedRole("INSTRUCTOR", null)));
    }
}
