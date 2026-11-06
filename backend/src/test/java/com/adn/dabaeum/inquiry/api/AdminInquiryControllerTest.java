package com.adn.dabaeum.inquiry.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.inquiry.application.InquiryApplicationService;
import com.adn.dabaeum.inquiry.application.InquiryViewPage;
import com.adn.dabaeum.inquiry.domain.InquiryStatus;
import com.adn.dabaeum.inquiry.domain.InquiryType;
import com.adn.dabaeum.inquiry.domain.InquiryView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 스펙 #42 · B-7 플랫폼 관리자 사이트 전체 문의 목록.
 *
 * <p>가장 위험한 경계는 {@code /admin/*} 이 PLATFORM_ADMIN 전용이라는 점이다 — 기관 관리자는
 * 여전히 {@code /institutions/{id}/inquiries} 를 써야 한다.
 */
@WebMvcTest(AdminInquiryController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=admin-inquiry-token")
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
class AdminInquiryControllerTest {

    private static final UUID COURSE_ID = UUID.fromString("90000000-0000-0000-0000-000000000009");
    private static final UUID USER_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID INSTITUTION_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");

    @Autowired MockMvc mockMvc;
    @MockitoBean InquiryApplicationService service;

    @Test
    void 전_기관_문의를_한_목록으로_본다() throws Exception {
        stubAnyList(page(InquiryStatus.PENDING));

        mockMvc.perform(get("/api/v1/admin/inquiries").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].institutionName").exists())
            .andExpect(jsonPath("$.data[0].courseTitle").exists())
            .andExpect(jsonPath("$.data[0].instructorName").exists());
    }

    @Test
    void 답변_상태로_거른다() throws Exception {
        stubAnyList(page(InquiryStatus.PENDING));

        mockMvc.perform(get("/api/v1/admin/inquiries").param("status", "PENDING")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].status", everyItem(equalTo("PENDING"))));

        verify(service).listAll(
            eq(InquiryStatus.PENDING), isNull(), isNull(), isNull(), isNull(), isNull(),
            anyInt(), anyInt());
    }

    @Test
    void 문의유형으로_거른다() throws Exception {
        stubAnyList(page(InquiryStatus.PENDING));

        mockMvc.perform(get("/api/v1/admin/inquiries").param("inquiryType", "QNA")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());

        verify(service).listAll(
            isNull(), eq(InquiryType.QNA), isNull(), isNull(), isNull(), isNull(),
            anyInt(), anyInt());
    }

    @Test
    void 수업방식으로도_거른다() throws Exception {
        stubAnyList(page(InquiryStatus.PENDING));

        mockMvc.perform(get("/api/v1/admin/inquiries").param("educationType", "OFFLINE")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());

        verify(service).listAll(
            isNull(), isNull(), eq(CourseEducationType.OFFLINE), isNull(), isNull(), isNull(),
            anyInt(), anyInt());
    }

    @Test
    void 기관과_강의로도_거른다() throws Exception {
        stubAnyList(page(InquiryStatus.PENDING));

        mockMvc.perform(get("/api/v1/admin/inquiries")
                .param("institutionId", INSTITUTION_ID.toString())
                .param("courseId", COURSE_ID.toString())
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());

        verify(service).listAll(
            isNull(), isNull(), isNull(), eq(INSTITUTION_ID), eq(COURSE_ID), isNull(),
            anyInt(), anyInt());
    }

    @Test
    void 검색어로_거른다() throws Exception {
        stubAnyList(page(InquiryStatus.PENDING));

        mockMvc.perform(get("/api/v1/admin/inquiries").param("q", "AI 기초")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk());

        verify(service).listAll(
            isNull(), isNull(), isNull(), isNull(), isNull(), eq("ai 기초"),
            anyInt(), anyInt());
    }

    @Test
    void 검색어가_너무_길면_422다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries").param("q", "가".repeat(101))
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 알_수_없는_상태값은_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries").param("status", "NOPE")
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void 기관_관리자는_전체_문의를_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries")
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void 학습자는_전체_문의를_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries")
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
    }

    /** 어떤 필터 조합이 오든 서비스가 뭘 받든 목이 같은 페이지를 준다 — 필터별 인자만 검증하면 된다. */
    private void stubAnyList(InquiryViewPage result) {
        lenient().when(service.listAll(
                any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(result);
    }

    private InquiryViewPage page(InquiryStatus status) {
        return new InquiryViewPage(List.of(inquiry(status)), 0, 20, 1, 1);
    }

    private InquiryView inquiry(InquiryStatus status) {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        EnrolleeProfile profile = new EnrolleeProfile(
            "시드학습자", null, null, "seed.learner@dabaeum.kr", "010-1234-5678", null);
        return new InquiryView(
            UUID.randomUUID(), USER_ID, COURSE_ID, "문의 제목", status, InquiryType.COURSE,
            now, "AI 기초", profile, "다배움기관", "강사", "TSIX_010201", "OFFLINE", 5, "태그",
            null, null, null);
    }
}
