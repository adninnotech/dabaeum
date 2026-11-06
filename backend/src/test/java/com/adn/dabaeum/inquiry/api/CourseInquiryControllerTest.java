package com.adn.dabaeum.inquiry.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.inquiry.application.CourseInquiryViewPage;
import com.adn.dabaeum.inquiry.application.CreateInquiryCommand;
import com.adn.dabaeum.inquiry.application.InquiryApplicationService;
import com.adn.dabaeum.inquiry.application.InquiryViewPage;
import com.adn.dabaeum.inquiry.domain.Inquiry;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 스펙 #31·C-6 강좌별 문의 목록. E-2 학습자 Q&A 도 같은 엔드포인트를 쓴다. */
@WebMvcTest(InquiryController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=course-inquiry-token")
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
class CourseInquiryControllerTest {

    private static final UUID COURSE_ID = UUID.fromString("90000000-0000-0000-0000-000000000009");
    private static final UUID USER_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID INSTITUTION_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final UUID PROFILE_IMAGE_ID = UUID.fromString("60000000-0000-0000-0000-000000000006");

    @Autowired MockMvc mockMvc;
    @MockitoBean InquiryApplicationService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void 강좌별_문의를_조회한다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.listForCourse(any(), eq(COURSE_ID), any(), anyInt(), anyInt()))
            .thenReturn(managerPage(courseInquiry(InquiryType.COURSE)));

        mockMvc.perform(get("/api/v1/courses/{courseId}/inquiries", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].inquiryType").value("COURSE"))
            .andExpect(jsonPath("$.data[0].userName").exists());
    }

    @Test
    void 관리자에게는_작성자_실명이_보인다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.listForCourse(any(), eq(COURSE_ID), any(), anyInt(), anyInt()))
            .thenReturn(managerPage(courseInquiry(InquiryType.COURSE)));

        mockMvc.perform(get("/api/v1/courses/{courseId}/inquiries", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(jsonPath("$.data[0].userName").value("시드학습자"))
            .andExpect(jsonPath("$.data[0].email").value("seed.learner@dabaeum.kr"))
            .andExpect(jsonPath("$.data[0].profileImageId").value(PROFILE_IMAGE_ID.toString()));
    }

    @Test
    void 학습자에게는_작성자가_마스킹된다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(service.listForCourse(any(), eq(COURSE_ID), any(), anyInt(), anyInt()))
            .thenReturn(new CourseInquiryViewPage(
                new InquiryViewPage(List.of(courseInquiry(InquiryType.QNA)), 0, 20, 1, 1), false));

        mockMvc.perform(get("/api/v1/courses/{courseId}/inquiries", COURSE_ID)
                .with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].userName").value("시***자"))
            .andExpect(jsonPath("$.data[0].email").doesNotExist())
            .andExpect(jsonPath("$.data[0].phone").doesNotExist())
            .andExpect(jsonPath("$.data[0].profileImageId").doesNotExist());
    }

    @Test
    void 유형으로_거를_수_있다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(manager());
        when(service.listForCourse(any(), eq(COURSE_ID), eq(InquiryType.QNA), anyInt(), anyInt()))
            .thenReturn(new CourseInquiryViewPage(new InquiryViewPage(
                List.of(courseInquiry(InquiryType.QNA), courseInquiry(InquiryType.QNA)),
                0, 20, 2, 1), true));

        mockMvc.perform(get("/api/v1/courses/{courseId}/inquiries", COURSE_ID)
                .param("type", "QNA")
                .with(user("manager").roles("INSTITUTION_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].inquiryType", everyItem(equalTo("QNA"))));
    }

    @Test
    void 유형을_안_보내면_COURSE_로_생성된다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(learner());
        when(service.create(any())).thenAnswer(invocation -> {
            CreateInquiryCommand command = invocation.getArgument(0);
            Instant now = Instant.parse("2026-09-16T00:00:00Z");
            return new Inquiry(UUID.randomUUID(), USER_ID, command.courseId(),
                command.title(), command.content(), InquiryStatus.PENDING,
                null, null, null, now, now, command.type());
        });

        mockMvc.perform(post("/api/v1/inquiries").with(user("learner").roles("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title": "질문", "content": "본문",
                     "courseId": "%s"}
                    """.formatted(COURSE_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.inquiryType").value("COURSE"));
    }

    /**
     * 서비스가 목이므로 여기서 켜는 manager 플래그는 "서비스가 이미 그렇게 판정했다" 는 가정이다.
     * 그 판정 자체(다른 기관 강사는 false)는 DefaultInquiryApplicationServiceTest 가 증명한다.
     */
    private CourseInquiryViewPage managerPage(InquiryView view) {
        return new CourseInquiryViewPage(new InquiryViewPage(List.of(view), 0, 20, 1, 1), true);
    }

    private InquiryView courseInquiry(InquiryType type) {
        Instant now = Instant.parse("2026-09-16T00:00:00Z");
        EnrolleeProfile profile = new EnrolleeProfile(
            "시드학습자", null, null, "seed.learner@dabaeum.kr", "010-1234-5678", PROFILE_IMAGE_ID);
        return new InquiryView(
            UUID.randomUUID(), USER_ID, COURSE_ID, "문의 제목", InquiryStatus.PENDING, type,
            now, "AI 기초", profile, "다배움기관", "강사", "TSIX_010201", "OFFLINE", 5, "태그",
            null, null, null);
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }

    private AuthenticatedUserContext manager() {
        return new AuthenticatedUserContext(UUID.randomUUID(), "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID)));
    }
}
