package com.adn.dabaeum.publiccourse.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import com.adn.dabaeum.publiccourse.application.PublicCourseQueryService;
import com.adn.dabaeum.publiccourse.application.PublicCourseSyncService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공공데이터 평생학습강좌 (설계서 docs/specs/public-lifelong-courses.md §5).
 * 공개 조회는 무인증, 관리자 경로는 규칙표가 PLATFORM_ADMIN 으로 막는다.
 */
@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class PublicCourseController {

    private final PublicCourseQueryService queries;
    private final PublicCourseSyncService sync;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public PublicCourseController(
        PublicCourseQueryService queries, PublicCourseSyncService sync, CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.queries = queries;
        this.sync = sync;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    // ---- 공개 ----

    @GetMapping("/public-courses")
    public PublicCourseDtos.CoursePage list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String method,
        @RequestParam(name = "targetType", required = false) List<String> targetTypes,
        @RequestParam(required = false) String regionCode,
        @RequestParam(required = false) Boolean free,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "false") boolean includeEnded,
        @RequestParam(defaultValue = "false") boolean receiptOpen,
        @RequestParam(name = "operDay", required = false) List<String> operDays,
        @RequestParam(required = false) String timeOfDay,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "edcStartDate,asc") String sort,
        HttpServletRequest request
    ) {
        PublicCourseQueryService.Filter filter = new PublicCourseQueryService.Filter(
            q, method, targetTypes, regionCode, free, from, to, includeEnded, receiptOpen, operDays, timeOfDay);
        PublicCourseQueryService.ListQuery query = new PublicCourseQueryService.ListQuery(filter, page, size, sort);
        return PublicCourseDtos.CoursePage.from(queries.search(query), meta(request));
    }

    /** 목록과 같은 조건(구·군 제외)의 구·군별 건수. */
    @GetMapping("/public-courses/facets")
    public ApiResponse<PublicCourseDtos.RegionFacets> facets(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String method,
        @RequestParam(name = "targetType", required = false) List<String> targetTypes,
        @RequestParam(required = false) Boolean free,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "false") boolean includeEnded,
        @RequestParam(defaultValue = "false") boolean receiptOpen,
        @RequestParam(name = "operDay", required = false) List<String> operDays,
        @RequestParam(required = false) String timeOfDay,
        HttpServletRequest request
    ) {
        PublicCourseQueryService.Filter filter = new PublicCourseQueryService.Filter(
            q, method, targetTypes, null, free, from, to, includeEnded, receiptOpen, operDays, timeOfDay);
        return new ApiResponse<>(PublicCourseDtos.RegionFacets.from(queries.regionFacets(filter)), meta(request));
    }

    @GetMapping("/public-courses/{courseId}")
    public ApiResponse<PublicCourseDtos.Detail> detail(@PathVariable UUID courseId, HttpServletRequest request) {
        return new ApiResponse<>(PublicCourseDtos.Detail.from(queries.detail(courseId)), meta(request));
    }

    // ---- 플랫폼 관리자 ----

    @PostMapping("/admin/public-courses/sync")
    public ResponseEntity<ApiResponse<PublicCourseDtos.SyncStarted>> startSync(HttpServletRequest request) {
        UUID runId = sync.startManual(currentUserProvider.requireUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(new ApiResponse<>(new PublicCourseDtos.SyncStarted(runId), meta(request)));
    }

    @GetMapping("/admin/public-course-sync-runs")
    public PublicCourseDtos.SyncRunPage runs(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest request
    ) {
        return PublicCourseDtos.SyncRunPage.from(queries.runs(page, size), meta(request));
    }

    @GetMapping("/admin/public-course-sync-runs/{runId}")
    public ApiResponse<PublicCourseDtos.SyncRun> run(@PathVariable UUID runId, HttpServletRequest request) {
        return new ApiResponse<>(PublicCourseDtos.SyncRun.from(queries.run(runId)), meta(request));
    }

    private ApiMeta meta(HttpServletRequest request) {
        return new ApiMeta((String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME),
            OffsetDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")));
    }
}
