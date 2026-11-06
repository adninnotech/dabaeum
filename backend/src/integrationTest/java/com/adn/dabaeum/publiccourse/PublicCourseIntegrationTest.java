package com.adn.dabaeum.publiccourse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.publiccourse.application.PublicCourseNormalizer;
import com.adn.dabaeum.publiccourse.application.PublicCourseProperties;
import com.adn.dabaeum.publiccourse.application.PublicCourseQueryService;
import com.adn.dabaeum.publiccourse.application.PublicCourseSourceKeys;
import com.adn.dabaeum.publiccourse.application.PublicCourseSyncService;
import com.adn.dabaeum.publiccourse.application.port.PublicCourseSource;
import com.adn.dabaeum.publiccourse.domain.PublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourseMethod;
import com.adn.dabaeum.publiccourse.domain.PublicCourseRepository;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRunRepository;
import com.adn.dabaeum.publiccourse.domain.PublicCourseTimeOfDay;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.ObjectMapper;

/**
 * 설계서 docs/specs/public-lifelong-courses.md §3.4·§4·§5.1 — 실제 DB 에서 upsert·비활성·재활성, 배열·jsonb 왕복,
 * 공개 검색 조건(접수 중·요일·시간대 포함)과 구·군별 건수, 실행 이력의 RUNNING 유일성. 원천은 가짜이며 트랜잭션은 롤백된다.
 * 기관 코드는 실행마다 새로 만들어 dev 서버가 수집한 실제 강좌와 섞이지 않는다.
 */
class PublicCourseIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired PublicCourseRepository courses;
    @Autowired PublicCourseSyncRunRepository runs;
    @Autowired ObjectMapper objectMapper;

    private final Map<String, List<Map<String, String>>> source = new HashMap<>();
    private Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Test
    void syncPersistsUpdatesDeactivatesAndSearchFinds() {
        String instt = "IT" + UUID.randomUUID().toString().substring(0, 8);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        source.put(instt, List.of(
            row(instt, "요가 " + tag, "2026-01-05", "2099-03-01", "어르신+성인", "오프라인", "0", "대구광역시 수성구 1"),
            row(instt, "도자기 " + tag, "2026-01-01", "2026-02-01", "청소년", "온라인", "30000", "대구광역시 달서구 1"),
            row(instt, "필라테스 " + tag, "2026-02-01", "2099-04-01", "청년/직장인", "혼합", "45000", "")));

        PublicCourseSyncRun first = sync(instt);

        assertThat(first.status()).isEqualTo(PublicCourseSyncRun.Status.SUCCEEDED);
        assertThat(first.counts().inserted()).isEqualTo(3);
        assertThat(runs.findById(first.id())).get().satisfies(stored -> {
            assertThat(stored.institutionResults()).singleElement()
                .satisfies(result -> assertThat(result.fetched()).isEqualTo(3));
            assertThat(stored.finishedAt()).isNotNull();
        });

        PublicCourse yoga = only(query(tag, null, List.of(), null, null, null, null));
        assertThat(yoga.course().targetTypes()).containsExactly("어르신", "성인");
        assertThat(yoga.course().operDays()).containsExactly("월", "수");
        assertThat(yoga.course().startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(yoga.course().regionCode()).isEqualTo("26");
        assertThat(yoga.course().free()).isTrue();
        assertThat(yoga.rawJson()).contains("\"lctreNm\"", "요가");

        assertThat(courses.search(query(tag, PublicCourseMethod.ONLINE, List.of(), null, null, null, null), 10, 0))
            .extracting(course -> course.course().lectureName())
            .containsExactly("도자기 " + tag);
        assertThat(courses.search(query(tag, PublicCourseMethod.VISIT, List.of(), null, null, null, null), 10, 0))
            .isEmpty();
        assertThat(courses.count(new PublicCourseRepository.Query(lower(tag), null, List.of(), null, null, null, null,
            null, null, List.of(), null, PublicCourseRepository.SortField.LECTURE_NAME, true, null))).isEqualTo(3);
        assertThat(courses.search(query(tag, null, List.of("청년/직장인", "어르신"), null, null, null, null), 10, 0))
            .extracting(course -> course.course().lectureName())
            .containsExactlyInAnyOrder("요가 " + tag, "필라테스 " + tag);
        assertThat(courses.search(query(tag, null, List.of(), "29", null, null, null), 10, 0))
            .extracting(course -> course.course().lectureName())
            .containsExactly("도자기 " + tag);
        assertThat(courses.search(query(tag, null, List.of(), "11", null, null, null), 10, 0)).isEmpty();
        assertThat(courses.search(query(tag, null, List.of(), null, false, null, null), 10, 0))
            .extracting(course -> course.course().lectureName())
            .containsExactly("도자기 " + tag, "필라테스 " + tag);
        assertThat(courses.search(query(tag, null, List.of(), null, true, null, null), 10, 0))
            .extracting(course -> course.course().lectureName())
            .containsExactly("요가 " + tag);
        assertThat(courses.search(new PublicCourseRepository.Query(lower(tag), null, List.of(), null, null,
            LocalDate.of(2026, 3, 15), null, null, null, List.of(), null, PublicCourseRepository.SortField.EDC_START_DATE,
            false, null), 10, 0))
            .extracting(course -> course.course().lectureName())
            .containsExactly("필라테스 " + tag, "요가 " + tag);

        now = now.plus(Duration.ofDays(1));
        source.put(instt, List.of(
            row(instt, "요가 " + tag, "2026-01-05", "2099-03-01", "어르신+성인", "오프라인", "5000", "대구광역시 수성구 1"),
            row(instt, "필라테스 " + tag, "2026-02-01", "2099-04-01", "청년/직장인", "혼합", "45000", "")));

        PublicCourseSyncRun second = sync(instt);

        assertThat(second.counts().updated()).isEqualTo(1);
        assertThat(second.counts().unchanged()).isEqualTo(1);
        assertThat(second.counts().deactivated()).isEqualTo(1);
        assertThat(courses.findById(yoga.id())).get().satisfies(updated -> {
            assertThat(updated.course().costAmount()).isEqualTo(5000);
            assertThat(updated.firstSeenAt()).isEqualTo(yoga.firstSeenAt());
        });
        assertThat(courses.search(query(tag, null, List.of(), null, null, null, null), 10, 0))
            .extracting(course -> course.course().lectureName())
            .doesNotContain("도자기 " + tag);

        now = now.plus(Duration.ofDays(1));
        source.put(instt, List.of(
            row(instt, "요가 " + tag, "2026-01-05", "2099-03-01", "어르신+성인", "오프라인", "5000", "대구광역시 수성구 1"),
            row(instt, "도자기 " + tag, "2026-01-01", "2026-02-01", "청소년", "온라인", "30000", "대구광역시 달서구 1"),
            row(instt, "필라테스 " + tag, "2026-02-01", "2099-04-01", "청년/직장인", "혼합", "45000", "")));

        PublicCourseSyncRun third = sync(instt);

        assertThat(third.counts().updated()).isEqualTo(1);
        assertThat(courses.findStoredByInsttCode(instt).values()).allMatch(PublicCourseRepository.Stored::active);
    }

    @Test
    void defaultEndedFilterKeepsCoursesWithUnknownEndDate() {
        String instt = "IT" + UUID.randomUUID().toString().substring(0, 8);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        source.put(instt, List.of(
            row(instt, "끝난 " + tag, "2025-01-01", "2025-02-01", "성인", "오프라인", "0", ""),
            row(instt, "종료일미상 " + tag, "2026-01-01", "", "성인", "오프라인", "0", "")));
        sync(instt);

        assertThat(courses.search(new PublicCourseRepository.Query(lower(tag), null, List.of(), null, null, null,
            null, LocalDate.of(2026, 10, 8), null, List.of(), null, PublicCourseRepository.SortField.EDC_START_DATE,
            true, null), 10, 0))
            .extracting(course -> course.course().lectureName())
            .containsExactly("종료일미상 " + tag);
    }

    /** 접수 중(마감일 이후·시작일 이전 또는 미상), 요일 OR, 시작 시각 구간, 구·군별 건수. */
    @Test
    void receiptDayTimeFiltersAndRegionCounts() {
        String instt = "IT" + UUID.randomUUID().toString().substring(0, 8);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> base = row(instt, "기준 " + tag, "2026-11-01", "2026-12-01", "성인", "오프라인", "0",
            "대구광역시 수성구 1");
        source.put(instt, List.of(
            with(base, "lctreNm", "접수중오전 " + tag, "rceptStartDate", "2026-10-01", "rceptEndDate", "2026-10-20",
                "operDay", "토", "edcStartTime", "09:30"),
            with(base, "lctreNm", "접수시작미상오후 " + tag, "rceptStartDate", "", "rceptEndDate", "2026-10-10",
                "operDay", "월+일", "edcStartTime", "12:00", "edcRdnmadr", "대구광역시 달서구 1"),
            with(base, "lctreNm", "접수마감저녁 " + tag, "rceptStartDate", "2026-09-01", "rceptEndDate", "2026-10-07",
                "operDay", "화", "edcStartTime", "18:00"),
            with(base, "lctreNm", "접수전시각미상 " + tag, "rceptStartDate", "2026-10-15", "rceptEndDate", "2026-10-30",
                "operDay", "토", "edcStartTime", "", "edcRdnmadr", "")));
        sync(instt);
        LocalDate today = LocalDate.of(2026, 10, 8);

        assertThat(names(new PublicCourseRepository.Query(lower(tag), null, List.of(), null, null, null, null, null,
            today, List.of(), null, PublicCourseRepository.SortField.LECTURE_NAME, true, null)))
            .containsExactlyInAnyOrder("접수중오전 " + tag, "접수시작미상오후 " + tag);
        assertThat(names(new PublicCourseRepository.Query(lower(tag), null, List.of(), null, null, null, null, null,
            null, List.of("토", "일"), null, PublicCourseRepository.SortField.LECTURE_NAME, true, null)))
            .containsExactlyInAnyOrder("접수중오전 " + tag, "접수시작미상오후 " + tag, "접수전시각미상 " + tag);
        assertThat(names(timeQuery(tag, PublicCourseTimeOfDay.MORNING))).containsExactly("접수중오전 " + tag);
        assertThat(names(timeQuery(tag, PublicCourseTimeOfDay.AFTERNOON))).containsExactly("접수시작미상오후 " + tag);
        assertThat(names(timeQuery(tag, PublicCourseTimeOfDay.EVENING))).containsExactly("접수마감저녁 " + tag);

        PublicCourseRepository.Query all = new PublicCourseRepository.Query(lower(tag), null, List.of(), null, null,
            null, null, null, null, List.of(), null, PublicCourseRepository.SortField.LECTURE_NAME, true, null);
        assertThat(courses.countByRegion(all)).containsExactlyInAnyOrderEntriesOf(Map.of("26", 2L, "29", 1L));
    }

    /** 접수 중(마감 빠른 순) → 예정(시작 빠른 순) → 모름(교육 시작 순) → 마감(최근 마감 순). */
    @Test
    void receiptStatusSortOrdersOpenUpcomingUnknownClosed() {
        String instt = "IT" + UUID.randomUUID().toString().substring(0, 8);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> base = row(instt, "기준 " + tag, "2026-11-01", "2026-12-01", "성인", "오프라인", "0",
            "대구광역시 수성구 1");
        source.put(instt, List.of(
            with(base, "lctreNm", "마감먼저 " + tag, "rceptStartDate", "2026-08-01", "rceptEndDate", "2026-09-01"),
            with(base, "lctreNm", "중나중 " + tag, "rceptStartDate", "2026-10-01", "rceptEndDate", "2026-10-20"),
            with(base, "lctreNm", "모름늦게 " + tag, "rceptStartDate", "", "rceptEndDate", "",
                "edcStartDay", "2026-11-05"),
            with(base, "lctreNm", "예정나중 " + tag, "rceptStartDate", "2026-10-15", "rceptEndDate", "2026-10-30"),
            with(base, "lctreNm", "중먼저 " + tag, "rceptStartDate", "", "rceptEndDate", "2026-10-08"),
            with(base, "lctreNm", "마감최근 " + tag, "rceptStartDate", "2026-09-01", "rceptEndDate", "2026-10-07"),
            with(base, "lctreNm", "예정먼저 " + tag, "rceptStartDate", "2026-10-09", "rceptEndDate", ""),
            with(base, "lctreNm", "모름먼저 " + tag, "rceptStartDate", "", "rceptEndDate", "",
                "edcStartDay", "2026-11-01")));
        sync(instt);

        PublicCourseRepository.Query query = new PublicCourseRepository.Query(lower(tag), null, List.of(), null, null,
            null, null, null, null, List.of(), null, PublicCourseRepository.SortField.RECEIPT_STATUS, true,
            LocalDate.of(2026, 10, 8));
        assertThat(names(query)).containsExactly(
            "중먼저 " + tag, "중나중 " + tag, "예정먼저 " + tag, "예정나중 " + tag, "모름먼저 " + tag, "모름늦게 " + tag,
            "마감최근 " + tag, "마감먼저 " + tag);
        assertThat(courses.search(query, 3, 3)).extracting(course -> course.course().lectureName())
            .containsExactly("예정나중 " + tag, "모름먼저 " + tag, "모름늦게 " + tag);
    }

    private List<String> names(PublicCourseRepository.Query query) {
        return courses.search(query, 10, 0).stream().map(course -> course.course().lectureName()).toList();
    }

    private static PublicCourseRepository.Query timeQuery(String tag, PublicCourseTimeOfDay timeOfDay) {
        return new PublicCourseRepository.Query(lower(tag), null, List.of(), null, null, null, null, null, null,
            List.of(), timeOfDay, PublicCourseRepository.SortField.LECTURE_NAME, true, null);
    }

    private static Map<String, String> with(Map<String, String> base, String... pairs) {
        Map<String, String> copy = new LinkedHashMap<>(base);
        for (int index = 0; index < pairs.length; index += 2) {
            copy.put(pairs[index], pairs[index + 1]);
        }
        return copy;
    }

    /** 유일성 위반은 트랜잭션을 중단시키므로 마지막 문장이어야 한다. */
    @Test
    void onlyOneRunCanBeRunningAndStaleRunsAreClosed() {
        int closedBefore = runs.closeStaleRunning(Instant.now().minus(Duration.ofDays(3650)), Instant.now());
        assertThat(closedBefore).isZero();
        PublicCourseSyncRun stale = PublicCourseSyncRun.start(UUID.randomUUID(), PublicCourseSyncRun.Trigger.SCHEDULED,
            null, Instant.parse("2000-01-01T00:00:00Z"));
        runs.insert(stale);
        assertThat(runs.closeStaleRunning(Instant.parse("2000-01-02T00:00:00Z"), Instant.now())).isEqualTo(1);
        assertThat(runs.findById(stale.id())).get().satisfies(closed -> {
            assertThat(closed.status()).isEqualTo(PublicCourseSyncRun.Status.FAILED);
            assertThat(closed.errorCode()).isEqualTo("INTERRUPTED");
        });

        runs.insert(PublicCourseSyncRun.start(UUID.randomUUID(), PublicCourseSyncRun.Trigger.SCHEDULED, null,
            Instant.now()));
        assertThatThrownBy(() -> runs.insert(PublicCourseSyncRun.start(UUID.randomUUID(),
            PublicCourseSyncRun.Trigger.SCHEDULED, null, Instant.now())))
            .isInstanceOf(DuplicateKeyException.class);
    }

    private PublicCourseSyncRun sync(String instt) {
        PublicCourseSyncService service = new PublicCourseSyncService(
            (insttCode, pageNo, numOfRows) -> new PublicCourseSource.Page(
                pageNo == 1 ? source.getOrDefault(insttCode, List.of()) : List.of(),
                source.getOrDefault(insttCode, List.of()).size()),
            courses, runs,
            new PublicCourseProperties(true, null, "key", 1000, null, Duration.ZERO, null, List.of(instt)),
            new PublicCourseNormalizer(), new PublicCourseSourceKeys(), objectMapper,
            TransactionOperations.withoutTransaction(), Runnable::run, Clock.fixed(now, ZoneOffset.UTC),
            UUID::randomUUID);
        UUID runId = service.startScheduled().orElseThrow();
        return runs.findById(runId).orElseThrow();
    }

    private static PublicCourseRepository.Query query(
        String tag, PublicCourseMethod method, List<String> targetTypes, String region, Boolean free, LocalDate from,
        LocalDate endedFrom
    ) {
        return new PublicCourseRepository.Query(lower(tag), method, targetTypes, region, free, from, null, endedFrom,
            null, List.of(), null, PublicCourseRepository.SortField.LECTURE_NAME, true, null);
    }

    private static String lower(String tag) {
        return tag.toLowerCase(java.util.Locale.ROOT);
    }

    private PublicCourse only(PublicCourseRepository.Query query) {
        List<PublicCourse> found = new ArrayList<>(courses.search(query, 10, 0));
        found.removeIf(course -> !course.course().lectureName().startsWith("요가"));
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    private static Map<String, String> row(
        String instt, String name, String start, String end, String targets, String method, String cost, String address
    ) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("lctreNm", name);
        row.put("instrctrNm", "강사");
        row.put("edcStartDay", start);
        row.put("edcEndDay", end);
        row.put("edcStartTime", "10:00");
        row.put("edcColseTime", "12:00");
        row.put("lctreCo", "내용");
        row.put("edcTrgetType", targets);
        row.put("edcMthType", method);
        row.put("operDay", "월+수");
        row.put("edcPlace", "평생학습관");
        row.put("psncpa", "20");
        row.put("lctreCost", cost);
        row.put("edcRdnmadr", address);
        row.put("operInstitutionNm", "운영기관");
        row.put("operPhoneNumber", "053-000-0000");
        row.put("rceptStartDate", "2025-12-01");
        row.put("rceptEndDate", "2025-12-20");
        row.put("rceptMthType", "인터넷");
        row.put("slctnMthType", "선착순");
        row.put("homepageUrl", "lll.example.kr");
        row.put("oadtCtLctreYn", "N");
        row.put("pntBankAckestYn", "N");
        row.put("lrnAcnutAckestYn", " ");
        row.put("referenceDate", "2026-10-08");
        row.put("insttCode", instt);
        row.put("insttNm", "통합테스트기관");
        return row;
    }
}
