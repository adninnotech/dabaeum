package com.adn.dabaeum.publiccourse.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.publiccourse.application.PublicCourseQueryService;
import com.adn.dabaeum.publiccourse.domain.NormalizedPublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** 공공데이터 강좌 API 응답 (설계서 §5). 원본 행(raw)은 내보내지 않는다. */
final class PublicCourseDtos {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private PublicCourseDtos() {
    }

    record Summary(
        UUID id, String lectureName, String operInstitutionName, String regionCode, String method,
        List<String> targetTypes, LocalDate edcStartDate, LocalDate edcEndDate, String startTime, String endTime,
        List<String> operDays, String place, Integer capacity, Integer costAmount, Boolean free,
        LocalDate receiptStartDate, LocalDate receiptEndDate, String homepageUrl) {

        static Summary from(PublicCourse course) {
            NormalizedPublicCourse value = course.course();
            return new Summary(course.id(), value.lectureName(), value.operInstitutionName(), value.regionCode(),
                value.method() == null ? null : value.method().name(), value.targetTypes(), value.edcStartDate(),
                value.edcEndDate(), time(value.startTime()), time(value.endTime()), value.operDays(), value.place(),
                value.capacity(), value.costAmount(), value.free(), value.receiptStartDate(), value.receiptEndDate(),
                value.homepageUrl());
        }
    }

    record Detail(
        UUID id, String lectureName, String operInstitutionName, String regionCode, String method,
        List<String> targetTypes, LocalDate edcStartDate, LocalDate edcEndDate, String startTime, String endTime,
        List<String> operDays, String place, Integer capacity, Integer costAmount, Boolean free,
        LocalDate receiptStartDate, LocalDate receiptEndDate, String homepageUrl, String instructorName,
        String lectureContent, String roadAddress, String operPhone, String receiptMethod, String selectionMethod,
        Boolean trainingSubsidy, Boolean creditBank, Boolean learningAccount, LocalDate referenceDate,
        String insttName, boolean active) {

        static Detail from(PublicCourse course) {
            NormalizedPublicCourse value = course.course();
            return new Detail(course.id(), value.lectureName(), value.operInstitutionName(), value.regionCode(),
                value.method() == null ? null : value.method().name(), value.targetTypes(), value.edcStartDate(),
                value.edcEndDate(), time(value.startTime()), time(value.endTime()), value.operDays(), value.place(),
                value.capacity(), value.costAmount(), value.free(), value.receiptStartDate(), value.receiptEndDate(),
                value.homepageUrl(), value.instructorName(), value.lectureContent(), value.roadAddress(),
                value.operPhone(), value.receiptMethod(), value.selectionMethod(), value.trainingSubsidy(),
                value.creditBank(), value.learningAccount(), value.referenceDate(), value.insttName(),
                course.active());
        }
    }

    record CoursePage(List<Summary> data, PageMeta page, ApiMeta meta) {

        static CoursePage from(PublicCourseQueryService.Page<PublicCourse> page, ApiMeta meta) {
            return new CoursePage(page.items().stream().map(Summary::from).toList(), pageMeta(page), meta);
        }
    }

    record RegionCount(String regionCode, long count) {
    }

    record RegionFacets(long total, List<RegionCount> regions) {

        static RegionFacets from(PublicCourseQueryService.RegionFacets facets) {
            return new RegionFacets(facets.total(), facets.regions().stream()
                .map(region -> new RegionCount(region.regionCode(), region.count()))
                .toList());
        }
    }

    record SyncStarted(UUID runId) {
    }

    record SyncCounts(
        int fetched, int inserted, int updated, int unchanged, int deactivated, int skipped, int parseFailures,
        int keyCollisions) {
    }

    record InstitutionResult(String insttCode, String insttName, String status, int fetched, String errorCode) {
    }

    record SyncRun(
        UUID id, String trigger, UUID requestedBy, String status, Instant startedAt, Instant finishedAt,
        SyncCounts counts, List<InstitutionResult> institutionResults, String errorCode) {

        static SyncRun from(PublicCourseSyncRun run) {
            PublicCourseSyncRun.Counts counts = run.counts();
            return new SyncRun(run.id(), run.trigger().name(), run.requestedBy(), run.status().name(),
                run.startedAt(), run.finishedAt(),
                new SyncCounts(counts.fetched(), counts.inserted(), counts.updated(), counts.unchanged(),
                    counts.deactivated(), counts.skipped(), counts.parseFailures(), counts.keyCollisions()),
                run.institutionResults().stream()
                    .map(result -> new InstitutionResult(result.insttCode(), result.insttName(),
                        result.status().name(), result.fetched(), result.errorCode()))
                    .toList(),
                run.errorCode());
        }
    }

    record SyncRunPage(List<SyncRun> data, PageMeta page, ApiMeta meta) {

        static SyncRunPage from(PublicCourseQueryService.Page<PublicCourseSyncRun> page, ApiMeta meta) {
            return new SyncRunPage(page.items().stream().map(SyncRun::from).toList(), pageMeta(page), meta);
        }
    }

    private static PageMeta pageMeta(PublicCourseQueryService.Page<?> page) {
        return new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    private static String time(LocalTime value) {
        return value == null ? null : value.format(TIME);
    }
}
