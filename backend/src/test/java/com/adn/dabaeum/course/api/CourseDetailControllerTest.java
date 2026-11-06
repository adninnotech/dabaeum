package com.adn.dabaeum.course.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.adn.dabaeum.course.application.CourseApplicationService;
import com.adn.dabaeum.course.application.CreateCourseCommand;
import com.adn.dabaeum.course.application.UpdateCourseCommand;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.course.domain.CourseView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

@WebMvcTest(CourseController.class)
@ActiveProfiles("local")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=course-detail-test-token")
@Import({
    CourseApiMapper.class,
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class CourseDetailControllerTest {

    private static final UUID COURSE_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "1ca6d1e2-5398-4188-bfe6-e08da954b53d"
    );

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CourseApplicationService courseService;

    @MockitoBean
    CurrentUserProvider currentUserProvider;

    @Test
    void D1_D3_입력값이_명령으로_전달된다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(courseService.create(any(), any())).thenReturn(savedCourse());

        mockMvc.perform(post("/api/v1/courses")
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId": "1ca6d1e2-5398-4188-bfe6-e08da954b53d",
                      "title": "보드게임으로 배우는 소통",
                      "educationType": "OFFLINE",
                      "startDate": "2026-11-02",
                      "endDate": "2026-12-21",
                      "capacity": 20,
                      "detail": {
                        "tags": "#보드게임 #취미 #소통",
                        "targetAudiences": ["ADULTS", "MIDDLE_AGED_SENIORS"],
                        "instructorProfileVisible": true,
                        "tuitionType": "PAID",
                        "tuitionAmount": 40000,
                        "materialFeeType": "PAID",
                        "materialFeeAmount": 10000,
                        "completionEnabled": true,
                        "completionCriteria": "PROGRESS",
                        "requiredProgressRate": 80,
                        "issueCertificate": true,
                        "notes": "<p>편한 복장으로 오세요.</p>",
                        "materials": "노트북, 스마트폰",
                        "learningObjective": "<p>대화 기술을 익힌다.</p>",
                        "selectionMethod": "FCFS",
                        "waitlistCapacity": 5
                      }
                    }
                    """))
            .andExpect(status().isCreated());

        ArgumentCaptor<CreateCourseCommand> captor =
            ArgumentCaptor.forClass(CreateCourseCommand.class);
        verify(courseService).create(captor.capture(), any());
        var detail = captor.getValue().detail();
        assertThat(detail.tags()).isEqualTo("#보드게임 #취미 #소통");
        assertThat(detail.tuitionAmount()).isEqualByComparingTo(new BigDecimal("40000"));
        assertThat(detail.requiredProgressRate()).isEqualTo(80);
        assertThat(detail.waitlistCapacity()).isEqualTo(5);
    }

    @Test
    void detail_을_보내지_않으면_기본값이_들어간다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(courseService.create(any(), any())).thenReturn(savedCourse());

        mockMvc.perform(post("/api/v1/courses")
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId": "1ca6d1e2-5398-4188-bfe6-e08da954b53d",
                      "title": "제목만 있는 강좌",
                      "educationType": "ONLINE",
                      "startDate": "2026-11-02",
                      "endDate": "2026-12-21",
                      "capacity": 10
                    }
                    """))
            .andExpect(status().isCreated());

        ArgumentCaptor<CreateCourseCommand> captor =
            ArgumentCaptor.forClass(CreateCourseCommand.class);
        verify(courseService).create(captor.capture(), any());
        assertThat(captor.getValue().detail().tuitionType())
            .isEqualTo(CourseFeeType.FREE);
    }

    @Test
    void 유료인데_금액이_없으면_422() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());

        mockMvc.perform(post("/api/v1/courses")
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId": "1ca6d1e2-5398-4188-bfe6-e08da954b53d",
                      "title": "금액 빠진 유료 강좌",
                      "educationType": "OFFLINE",
                      "startDate": "2026-11-02",
                      "endDate": "2026-12-21",
                      "capacity": 10,
                      "detail": {"tuitionType": "PAID"}
                    }
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.details[0]").value("detail.tuitionAmount"));
    }

    @Test
    void 알_수_없는_수강대상은_422() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());

        mockMvc.perform(post("/api/v1/courses")
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "institutionId": "1ca6d1e2-5398-4188-bfe6-e08da954b53d",
                      "title": "잘못된 수강대상",
                      "educationType": "OFFLINE",
                      "startDate": "2026-11-02",
                      "endDate": "2026-12-21",
                      "capacity": 10,
                      "detail": {"targetAudiences": ["로봇"]}
                    }
                    """))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void detail_을_생략한_PUT_은_명령에_absent_를_담는다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(courseService.update(any(), any())).thenReturn(savedCourse());
        when(courseService.get(any(), any())).thenReturn(new CourseView(savedCourse(), 0, null, null));

        mockMvc.perform(put("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title": "수정된 제목"}
                    """))
            .andExpect(status().isOk());

        ArgumentCaptor<UpdateCourseCommand> captor =
            ArgumentCaptor.forClass(UpdateCourseCommand.class);
        verify(courseService).update(captor.capture(), any());
        assertThat(captor.getValue().detail().present()).isFalse();
    }

    @Test
    void detail_을_보낸_PUT_은_그_값이_명령에_담긴다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(courseService.update(any(), any())).thenReturn(savedCourse());
        when(courseService.get(any(), any())).thenReturn(new CourseView(savedCourse(), 0, null, null));

        mockMvc.perform(put("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "detail": {
                        "tags": "#야간반",
                        "tuitionType": "PAID",
                        "tuitionAmount": 30000
                      }
                    }
                    """))
            .andExpect(status().isOk());

        ArgumentCaptor<UpdateCourseCommand> captor =
            ArgumentCaptor.forClass(UpdateCourseCommand.class);
        verify(courseService).update(captor.capture(), any());
        assertThat(captor.getValue().detail().present()).isTrue();
        assertThat(captor.getValue().detail().value().tags()).isEqualTo("#야간반");
    }

    @Test
    void PUT_의_description에_담긴_script는_정화된_값이_명령에_담긴다() throws Exception {
        when(currentUserProvider.requireContext()).thenReturn(instructor());
        when(courseService.update(any(), any())).thenReturn(savedCourse());
        when(courseService.get(any(), any())).thenReturn(new CourseView(savedCourse(), 0, null, null));

        mockMvc.perform(put("/api/v1/courses/{courseId}", COURSE_ID)
                .with(user("manager").roles("INSTITUTION_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "description": "<p>본문</p><script>alert('xss')</script>"
                    }
                    """))
            .andExpect(status().isOk());

        ArgumentCaptor<UpdateCourseCommand> captor =
            ArgumentCaptor.forClass(UpdateCourseCommand.class);
        verify(courseService).update(captor.capture(), any());
        assertThat(captor.getValue().description().value())
            .contains("본문")
            .doesNotContain("script")
            .doesNotContain("alert");
    }

    private AuthenticatedUserContext instructor() {
        return new AuthenticatedUserContext(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", INSTITUTION_ID))
        );
    }

    private Course savedCourse() {
        Instant instant = Instant.parse("2026-08-04T00:00:00Z");
        return new Course(
            COURSE_ID,
            INSTITUTION_ID,
            "COURSE-001",
            "과정 제목",
            null,
            null,
            CourseEducationType.OFFLINE,
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 12, 21),
            null,
            null,
            20,
            null,
            null,
            false,
            null,
            CourseStatus.DRAFT,
            instant,
            instant,
            null
        );
    }
}
