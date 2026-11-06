package com.adn.dabaeum.publiccourse.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.publiccourse.application.PublicCourseQueryService;
import com.adn.dabaeum.publiccourse.application.PublicCourseSyncService;
import com.adn.dabaeum.publiccourse.domain.NormalizedPublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourseMethod;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 설계서 §5 — 공개 목록·상세(무인증), 수집 실행·이력(플랫폼 관리자), 응답이 계약과 같은지. */
@WebMvcTest(PublicCourseController.class)
@ActiveProfiles("local")
@TestPropertySource(properties = "dabaeum.security.dev.bearer-token=public-course-token")
@Import({
    ApiExceptionHandler.class,
    ClockConfiguration.class,
    SecurityConfiguration.class,
    DevBearerSecurityConfiguration.class,
    LocalDevSecuritySupportConfiguration.class,
    JacksonConfiguration.class,
    RequestIdFilter.class
})
class PublicCourseControllerTest {

    private static final UUID COURSE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired MockMvc mockMvc;
    @MockitoBean PublicCourseQueryService queries;
    @MockitoBean PublicCourseSyncService sync;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void publicListNeedsNoLoginAndMatchesContract() throws Exception {
        when(queries.search(any())).thenReturn(
            new PublicCourseQueryService.Page<>(List.of(course(true)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/public-courses")
                .param("q", "필라테스")
                .param("method", "OFFLINE")
                .param("targetType", "지역주민,어르신")
                .param("regionCode", "26")
                .param("free", "false")
                .param("from", "2026-10-01")
                .param("includeEnded", "true")
                .param("receiptOpen", "true")
                .param("operDay", "토,일")
                .param("timeOfDay", "EVENING")
                .param("sort", "receiptEndDate,desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].lectureName").value("필라테스 평일"))
            .andExpect(jsonPath("$.data[0].startTime").value("14:10"))
            .andExpect(jsonPath("$.data[0].operDays[1]").value("수"))
            .andExpect(jsonPath("$.data[0].homepageUrl").value("https://lll.suseong.kr"))
            .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<PublicCourseQueryService.ListQuery> captor =
            ArgumentCaptor.forClass(PublicCourseQueryService.ListQuery.class);
        verify(queries).search(captor.capture());
        PublicCourseQueryService.ListQuery query = captor.getValue();
        assertThat(query.filter().targetTypes()).containsExactly("지역주민", "어르신");
        assertThat(query.filter().from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(query.filter().includeEnded()).isTrue();
        assertThat(query.filter().receiptOpen()).isTrue();
        assertThat(query.filter().operDays()).containsExactly("토", "일");
        assertThat(query.filter().timeOfDay()).isEqualTo("EVENING");
        assertThat(query.sort()).isEqualTo("receiptEndDate,desc");
    }

    @Test
    void publicRegionFacetsNeedNoLoginAndMatchContract() throws Exception {
        when(queries.regionFacets(any())).thenReturn(new PublicCourseQueryService.RegionFacets(470L, List.of(
            new PublicCourseQueryService.RegionCount("11", 0L),
            new PublicCourseQueryService.RegionCount("26", 416L))));

        mockMvc.perform(get("/api/v1/public-courses/facets")
                .param("q", "요가")
                .param("free", "true")
                .param("receiptOpen", "true")
                .param("operDay", "토")
                .param("timeOfDay", "MORNING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(470))
            .andExpect(jsonPath("$.data.regions[1].regionCode").value("26"))
            .andExpect(jsonPath("$.data.regions[1].count").value(416));

        ArgumentCaptor<PublicCourseQueryService.Filter> captor =
            ArgumentCaptor.forClass(PublicCourseQueryService.Filter.class);
        verify(queries).regionFacets(captor.capture());
        assertThat(captor.getValue().q()).isEqualTo("요가");
        assertThat(captor.getValue().regionCode()).isNull();
        assertThat(captor.getValue().receiptOpen()).isTrue();
        assertThat(captor.getValue().operDays()).containsExactly("토");
        assertThat(captor.getValue().timeOfDay()).isEqualTo("MORNING");
    }

    @Test
    void publicDetailReturnsInactiveCourseWithFlag() throws Exception {
        when(queries.detail(COURSE_ID)).thenReturn(course(false));

        mockMvc.perform(get("/api/v1/public-courses/{courseId}", COURSE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.active").value(false))
            .andExpect(jsonPath("$.data.operPhone").value("053-666-2693"))
            .andExpect(jsonPath("$.data.raw").doesNotExist());
    }

    @Test
    void unknownCourseIsNotFound() throws Exception {
        when(queries.detail(COURSE_ID)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
            ApiErrorCode.PUBLIC_COURSE_NOT_FOUND, "Public course not found"));

        mockMvc.perform(get("/api/v1/public-courses/{courseId}", COURSE_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PUBLIC_COURSE_NOT_FOUND"));
    }

    @Test
    void adminStartsSyncAndGetsAccepted() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(ADMIN_ID);
        when(sync.startManual(ADMIN_ID)).thenReturn(RUN_ID);

        mockMvc.perform(post("/api/v1/admin/public-courses/sync").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.runId").value(RUN_ID.toString()));
    }

    @Test
    void runningSyncIsConflict() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(ADMIN_ID);
        when(sync.startManual(ADMIN_ID)).thenThrow(new ApiException(HttpStatus.CONFLICT,
            ApiErrorCode.PUBLIC_COURSE_SYNC_RUNNING, "Public course sync is already running"));

        mockMvc.perform(post("/api/v1/admin/public-courses/sync").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PUBLIC_COURSE_SYNC_RUNNING"));
    }

    @Test
    void nonAdminCannotStartSyncOrReadRuns() throws Exception {
        mockMvc.perform(post("/api/v1/admin/public-courses/sync").with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/public-course-sync-runs").with(user("learner").roles("LEARNER")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(sync);
    }

    @Test
    void adminReadsRunsAndRunDetail() throws Exception {
        PublicCourseSyncRun run = new PublicCourseSyncRun(RUN_ID, PublicCourseSyncRun.Trigger.MANUAL, ADMIN_ID,
            PublicCourseSyncRun.Status.PARTIAL, Instant.parse("2026-10-08T18:00:00Z"),
            Instant.parse("2026-10-08T18:00:40Z"),
            new PublicCourseSyncRun.Counts(2877, 2877, 0, 0, 0, 0, 3, 5),
            List.of(
                new PublicCourseSyncRun.InstitutionResult("3460000", "대구광역시 수성구",
                    PublicCourseSyncRun.InstitutionStatus.SUCCEEDED, 416, null),
                new PublicCourseSyncRun.InstitutionResult("3470000", null,
                    PublicCourseSyncRun.InstitutionStatus.FAILED, 0, "PUBLIC_DATA_22")),
            null);
        when(queries.runs(0, 20)).thenReturn(new PublicCourseQueryService.Page<>(List.of(run), 0, 20, 1, 1));
        when(queries.run(RUN_ID)).thenReturn(run);

        mockMvc.perform(get("/api/v1/admin/public-course-sync-runs").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("PARTIAL"))
            .andExpect(jsonPath("$.data[0].counts.keyCollisions").value(5));
        mockMvc.perform(get("/api/v1/admin/public-course-sync-runs/{runId}", RUN_ID)
                .with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.institutionResults[1].errorCode").value("PUBLIC_DATA_22"));
    }

    private static PublicCourse course(boolean active) {
        NormalizedPublicCourse value = new NormalizedPublicCourse("3460000", "대구광역시 수성구", "필라테스 평일",
            "김경희", "소도구를 이용한 근력강화", "수성동평생학습센터", "대구광역시 수성구 명덕로 73길 7",
            "대구광역시 수성구청", "053-666-2693", "https://lll.suseong.kr", LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 11, 23), LocalTime.of(14, 10), LocalTime.of(15, 0), List.of("월", "수"),
            List.of("지역주민"), PublicCourseMethod.OFFLINE, "26", 25, 45000, false, LocalDate.of(2026, 8, 4),
            LocalDate.of(2026, 8, 10), "인터넷", "선착순", false, false, null, LocalDate.of(2026, 8, 10));
        Instant seen = Instant.parse("2026-10-08T18:00:00Z");
        return new PublicCourse(COURSE_ID, "a".repeat(64), "b".repeat(64), value, "{}", active, seen, seen,
            active ? null : seen);
    }
}
