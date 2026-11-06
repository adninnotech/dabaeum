package com.adn.dabaeum.enrollment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.adn.dabaeum.enrollment.application.EnrollmentQueryService;
import com.adn.dabaeum.enrollment.application.LearningCoursePage;
import com.adn.dabaeum.inquiry.api.InquiryApiMapper;
import com.adn.dabaeum.inquiry.api.InquiryController;
import com.adn.dabaeum.inquiry.application.InquiryApplicationService;
import com.adn.dabaeum.inquiry.application.InquiryViewPage;
import com.adn.dabaeum.interest.api.CourseInterestController;
import com.adn.dabaeum.interest.application.CourseInterestApplicationService;
import com.adn.dabaeum.interest.application.CourseInterestPage;
import com.adn.dabaeum.review.api.CourseReviewController;
import com.adn.dabaeum.review.application.CourseReviewApplicationService;
import com.adn.dabaeum.review.application.CourseReviewPage;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 학습관리 4탭(수강 중·관심·문의·후기)이 전부 q 검색 파라미터를 받아 계약대로 응답하는지,
 * 그리고 KeywordQuery 로 정규화된 값을 서비스로 그대로 넘기는지 본다. 서비스는 mock 이라
 * 실제 LIKE/ESCAPE 동작은 LearnerListSearchIntegrationTest 가 검증한다.
 */
@WebMvcTest({
    EnrollmentQueryController.class,
    CourseInterestController.class,
    InquiryController.class,
    CourseReviewController.class
})
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=learner-search-token")
@Import({
    EnrollmentQueryApiMapper.class,
    InquiryApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class LearnerListSearchTest {

    private static final UUID USER_ID = UUID.fromString(
        "40000000-0000-0000-0000-000000000005");

    @Autowired MockMvc mockMvc;
    @MockitoBean EnrollmentQueryService enrollmentQueryService;
    @MockitoBean CourseInterestApplicationService courseInterestApplicationService;
    @MockitoBean InquiryApplicationService inquiryApplicationService;
    @MockitoBean CourseReviewApplicationService courseReviewApplicationService;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserProvider.requireContext()).thenReturn(learner());
        lenient().when(enrollmentQueryService.listLearningCourses(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new LearningCoursePage(List.of(), 0, 20, 0, 0));
        lenient().when(courseInterestApplicationService.listMine(any(), any(), anyInt(), anyInt()))
            .thenReturn(new CourseInterestPage(List.of(), 0, 20, 0, 0));
        lenient().when(inquiryApplicationService.listMine(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new InquiryViewPage(List.of(), 0, 20, 0, 0));
        lenient().when(courseReviewApplicationService.listMine(any(), any(), anyInt(), anyInt()))
            .thenReturn(new CourseReviewPage(List.of(), 0, 20, 0, 0));
    }

    @Test
    void 네_목록이_모두_q_를_받는다() throws Exception {
        for (String path : List.of(
                "/api/v1/users/me/learning-courses",
                "/api/v1/users/me/interests",
                "/api/v1/users/me/inquiries",
                "/api/v1/users/me/reviews")) {
            mockMvc.perform(get(path).param("q", "수채화").with(user("learner").roles("LEARNER")))
                .andExpect(status().isOk());
        }
    }

    @Test
    void 정규화된_키워드가_그대로_서비스로_전달된다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/learning-courses")
                .param("q", "  수채화  ").with(user("learner").roles("LEARNER")))
            .andExpect(status().isOk());

        verify(enrollmentQueryService).listLearningCourses(
            any(), any(), eq("수채화"), anyInt(), anyInt());
    }

    @Test
    void q_가_100자를_넘으면_422() throws Exception {
        // 계약이 q 를 maxLength 100 으로 선언하므로 101자 요청은 요청 단계에서 계약 위반이다.
        // 일부러 잘못된 요청이라 openApi().isValid 를 붙이지 않는다(GET /courses 의 같은 테스트도 그렇다).
        String tooLong = "가".repeat(101);
        mockMvc.perform(get("/api/v1/users/me/reviews")
                .param("q", tooLong).with(user("learner").roles("LEARNER")))
            .andExpect(status().isUnprocessableEntity());
    }

    private AuthenticatedUserContext learner() {
        return new AuthenticatedUserContext(USER_ID, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", null)));
    }
}
