package com.adn.dabaeum.publiccourse.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.publiccourse.domain.PublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourseRepository;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun.InstitutionStatus;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun.Status;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRunRepository;
import com.adn.dabaeum.publiccourse.application.port.PublicCourseSource;
import com.adn.dabaeum.publiccourse.application.port.PublicCourseSourceException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

/** 설계서 §3.4·§3.5 — 기관 단위 반영, 비활성 건너뜀, 실행 상태, 동시 실행 차단. */
class PublicCourseSyncServiceTest {

    private static final String SUSEONG = "3460000";
    private static final String DALSEO = "3470000";
    private static final UUID ADMIN = UUID.randomUUID();

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-08T18:00:00Z"));
    private FakeSource source;
    private FakeCourses courses;
    private FakeRuns runs;

    @BeforeEach
    void setUp() {
        source = new FakeSource();
        courses = new FakeCourses();
        runs = new FakeRuns();
    }

    @Test
    void firstSyncInsertsEveryRowAndSucceeds() {
        source.put(SUSEONG, row("요가", "10"), row("필라테스", "20"));
        source.put(DALSEO, row(DALSEO, "도자기", "15"));

        PublicCourseSyncRun run = finished(service(SUSEONG, DALSEO).startScheduled().orElseThrow());

        assertThat(run.status()).isEqualTo(Status.SUCCEEDED);
        assertThat(run.counts().fetched()).isEqualTo(3);
        assertThat(run.counts().inserted()).isEqualTo(3);
        assertThat(run.institutionResults()).extracting(PublicCourseSyncRun.InstitutionResult::status)
            .containsExactly(InstitutionStatus.SUCCEEDED, InstitutionStatus.SUCCEEDED);
        assertThat(courses.byKey.values()).allMatch(PublicCourse::active);
        assertThat(courses.byKey.values()).extracting(course -> course.course().lectureName())
            .containsExactlyInAnyOrder("요가", "필라테스", "도자기");
    }

    @Test
    void resyncUpdatesChangedTouchesUnchangedAndDeactivatesMissing() {
        source.put(SUSEONG, row("요가", "10"), row("필라테스", "20"), row("도자기", "15"));
        service(SUSEONG).startScheduled();
        now.set(now.get().plus(Duration.ofDays(1)));

        source.put(SUSEONG, row("요가", "12"), row("필라테스", "20"));
        PublicCourseSyncRun run = finished(service(SUSEONG).startScheduled().orElseThrow());

        assertThat(run.counts().updated()).isEqualTo(1);
        assertThat(run.counts().unchanged()).isEqualTo(1);
        assertThat(run.counts().deactivated()).isEqualTo(1);
        assertThat(courses.named("요가").course().capacity()).isEqualTo(12);
        assertThat(courses.named("필라테스").lastSeenAt()).isEqualTo(now.get());
        assertThat(courses.named("도자기").active()).isFalse();
    }

    @Test
    void reappearingCourseIsReactivatedWithSameId() {
        source.put(SUSEONG, row("요가", "10"), row("필라테스", "20"));
        service(SUSEONG).startScheduled();
        UUID id = courses.named("필라테스").id();
        now.set(now.get().plus(Duration.ofDays(1)));
        source.put(SUSEONG, row("요가", "10"));
        service(SUSEONG).startScheduled();
        now.set(now.get().plus(Duration.ofDays(1)));

        source.put(SUSEONG, row("요가", "10"), row("필라테스", "20"));
        PublicCourseSyncRun run = finished(service(SUSEONG).startScheduled().orElseThrow());

        assertThat(courses.named("필라테스").active()).isTrue();
        assertThat(courses.named("필라테스").id()).isEqualTo(id);
        assertThat(run.counts().updated()).isEqualTo(1);
        assertThat(run.counts().unchanged()).isEqualTo(1);
    }

    @Test
    void failedInstitutionKeepsItsCoursesAndRunIsPartial() {
        source.put(SUSEONG, row("요가", "10"));
        source.put(DALSEO, row(DALSEO, "도자기", "15"));
        service(SUSEONG, DALSEO).startScheduled();
        now.set(now.get().plus(Duration.ofDays(1)));

        source.fail(DALSEO, "PUBLIC_DATA_22");
        PublicCourseSyncRun run = finished(service(SUSEONG, DALSEO).startScheduled().orElseThrow());

        assertThat(run.status()).isEqualTo(Status.PARTIAL);
        assertThat(run.institutionResults()).filteredOn(result -> result.insttCode().equals(DALSEO))
            .singleElement().satisfies(result -> {
                assertThat(result.status()).isEqualTo(InstitutionStatus.FAILED);
                assertThat(result.errorCode()).isEqualTo("PUBLIC_DATA_22");
            });
        assertThat(courses.named("도자기").active()).isTrue();
        assertThat(run.counts().deactivated()).isZero();
    }

    @Test
    void emptyInstitutionResponseDoesNotDeactivate() {
        source.put(SUSEONG, row("요가", "10"));
        service(SUSEONG).startScheduled();
        now.set(now.get().plus(Duration.ofDays(1)));

        source.put(SUSEONG);
        PublicCourseSyncRun run = finished(service(SUSEONG).startScheduled().orElseThrow());

        assertThat(run.status()).isEqualTo(Status.PARTIAL);
        assertThat(run.institutionResults()).singleElement()
            .extracting(PublicCourseSyncRun.InstitutionResult::status).isEqualTo(InstitutionStatus.EMPTY);
        assertThat(courses.named("요가").active()).isTrue();
    }

    @Test
    void allInstitutionsFailingFailsTheRun() {
        source.fail(SUSEONG, "PUBLIC_DATA_30");

        PublicCourseSyncRun run = finished(service(SUSEONG).startScheduled().orElseThrow());

        assertThat(run.status()).isEqualTo(Status.FAILED);
        assertThat(run.errorCode()).isEqualTo("ALL_INSTITUTIONS_FAILED");
    }

    @Test
    void missingServiceKeyFailsBeforeCallingSource() {
        PublicCourseSyncService service = service(properties(null, SUSEONG));

        PublicCourseSyncRun run = finished(service.startScheduled().orElseThrow());

        assertThat(run.status()).isEqualTo(Status.FAILED);
        assertThat(run.errorCode()).isEqualTo("SERVICE_KEY_MISSING");
        assertThat(source.calls).isEmpty();
    }

    @Test
    void fetchesEveryPageUsingTotalCount() {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            rows.add(row("강좌" + index, "10"));
        }
        source.put(SUSEONG, rows.toArray(Map[]::new));
        PublicCourseSyncService service = service(new PublicCourseProperties(true, null, "key", 2, null,
            Duration.ZERO, null, List.of(SUSEONG)));

        PublicCourseSyncRun run = finished(service.startScheduled().orElseThrow());

        assertThat(source.calls).containsExactly(SUSEONG + "#1", SUSEONG + "#2", SUSEONG + "#3");
        assertThat(run.counts().inserted()).isEqualTo(5);
    }

    @Test
    void skippedRowsParseFailuresAndCollisionsAreCounted() {
        Map<String, String> noName = row("", "10");
        Map<String, String> badCost = row("요가", "10");
        badCost.put("lctreCost", "3만원");
        Map<String, String> twin = row("필라테스", "20");
        Map<String, String> twinSmall = row("필라테스", "5");
        source.put(SUSEONG, noName, badCost, twin, twinSmall);

        PublicCourseSyncRun run = finished(service(SUSEONG).startScheduled().orElseThrow());

        assertThat(run.counts().skipped()).isEqualTo(1);
        assertThat(run.counts().parseFailures()).isEqualTo(1);
        assertThat(run.counts().keyCollisions()).isEqualTo(1);
        assertThat(run.counts().inserted()).isEqualTo(3);
    }

    @Test
    void manualSyncWhileRunningIsConflict() {
        runs.blockNext = true;

        assertThatThrownBy(() -> service(SUSEONG).startManual(ADMIN))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo(ApiErrorCode.PUBLIC_COURSE_SYNC_RUNNING);
    }

    @Test
    void scheduledSyncWhileRunningIsSkipped() {
        runs.blockNext = true;

        assertThat(service(SUSEONG).startScheduled()).isEmpty();
    }

    @Test
    void manualSyncRecordsRequester() {
        source.put(SUSEONG, row("요가", "10"));

        PublicCourseSyncRun run = finished(service(SUSEONG).startManual(ADMIN));

        assertThat(run.trigger()).isEqualTo(PublicCourseSyncRun.Trigger.MANUAL);
        assertThat(run.requestedBy()).isEqualTo(ADMIN);
    }

    @Test
    void staleRunningRunsAreClosedBeforeStarting() {
        source.put(SUSEONG, row("요가", "10"));

        service(SUSEONG).startScheduled();

        assertThat(runs.closedBefore).isEqualTo(now.get().minus(Duration.ofHours(1)));
    }

    @Test
    void incompleteLaterPageFailsInstitutionWithoutDeactivating() {
        source.put(SUSEONG, row("요가", "10"), row("필라테스", "20"), row("도자기", "15"));
        PublicCourseSyncService paged = service(new PublicCourseProperties(true, null, "key", 2, null,
            Duration.ZERO, null, List.of(SUSEONG)));
        paged.startScheduled();
        now.set(now.get().plus(Duration.ofDays(1)));

        source.emptyFromPage(SUSEONG, 2);
        PublicCourseSyncRun run = finished(paged.startScheduled().orElseThrow());

        assertThat(run.institutionResults()).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(InstitutionStatus.FAILED);
            assertThat(result.errorCode()).isEqualTo("PUBLIC_DATA_INCOMPLETE");
        });
        assertThat(courses.byKey.values()).allMatch(PublicCourse::active);
    }

    @Test
    void absurdTotalCountFailsWithoutFetchingEveryPage() {
        source.put(SUSEONG, row("요가", "10"));
        source.overrideTotal(SUSEONG, 999_999_999);

        PublicCourseSyncRun run = finished(service(SUSEONG).startScheduled().orElseThrow());

        assertThat(run.institutionResults()).singleElement()
            .extracting(PublicCourseSyncRun.InstitutionResult::errorCode).isEqualTo("PUBLIC_DATA_INVALID_RESPONSE");
        assertThat(source.calls).hasSize(1);
    }

    @Test
    void databaseErrorFailsOnlyThatInstitution() {
        source.put(SUSEONG, row("요가", "10"));
        source.put(DALSEO, row(DALSEO, "도자기", "15"));
        courses.failOn = "요가";

        PublicCourseSyncRun run = finished(service(SUSEONG, DALSEO).startScheduled().orElseThrow());

        assertThat(run.status()).isEqualTo(Status.PARTIAL);
        assertThat(run.institutionResults()).extracting(PublicCourseSyncRun.InstitutionResult::errorCode)
            .containsExactly("DB_ERROR", null);
        assertThat(run.counts().inserted()).isEqualTo(1);
    }

    @Test
    void rowOfAnotherInstitutionIsSkippedAndBlankCodeIsFilled() {
        Map<String, String> other = row(DALSEO, "도자기", "15");
        Map<String, String> blank = row("", "요가", "10");
        source.put(SUSEONG, other, blank);

        PublicCourseSyncRun run = finished(service(SUSEONG).startScheduled().orElseThrow());

        assertThat(run.counts().skipped()).isEqualTo(1);
        assertThat(courses.named("요가").course().insttCode()).isEqualTo(SUSEONG);
    }

    @Test
    void nulCharactersAndLoneSurrogatesAreRemovedBeforeSaving() {
        source.put(SUSEONG, row("요\u0000가\uD800", "10"));

        finished(service(SUSEONG).startScheduled().orElseThrow());

        PublicCourse saved = courses.named("요가");
        assertThat(saved.rawJson()).doesNotContain("\\u0000").doesNotContain("\u0000");
    }

    @Test
    void rejectedExecutionClosesTheRunImmediately() {
        PublicCourseSyncService rejecting = service(properties("key", SUSEONG), task -> {
            throw new java.util.concurrent.RejectedExecutionException("full");
        });

        UUID runId = rejecting.startScheduled().orElseThrow();

        assertThat(runs.byId.get(runId).status()).isEqualTo(Status.FAILED);
        assertThat(runs.byId.get(runId).errorCode()).isEqualTo("EXECUTOR_REJECTED");
    }

    @Test
    void interruptStopsTheWholeRun() {
        source.put(SUSEONG, row("요가", "10"));
        source.put(DALSEO, row(DALSEO, "도자기", "15"));
        PublicCourseSyncService pausing = service(new PublicCourseProperties(true, null, "key", 1000, null,
            Duration.ofMillis(1), null, List.of(SUSEONG, DALSEO)));

        Thread.currentThread().interrupt();
        UUID runId;
        try {
            runId = pausing.startScheduled().orElseThrow();
        } finally {
            Thread.interrupted();
        }

        assertThat(runs.byId.get(runId).status()).isEqualTo(Status.FAILED);
        assertThat(runs.byId.get(runId).errorCode()).isEqualTo("INTERRUPTED");
        assertThat(source.calls).doesNotContain(DALSEO + "#1");
    }

    private PublicCourseSyncRun finished(UUID runId) {
        PublicCourseSyncRun run = runs.byId.get(runId);
        assertThat(run.status()).isNotEqualTo(Status.RUNNING);
        return run;
    }

    private PublicCourseSyncService service(String... codes) {
        return service(properties("key", codes));
    }

    private PublicCourseSyncService service(PublicCourseProperties properties) {
        return service(properties, Runnable::run);
    }

    private PublicCourseSyncService service(PublicCourseProperties properties, java.util.concurrent.Executor executor) {
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        return new PublicCourseSyncService(source, courses, runs, properties, new PublicCourseNormalizer(),
            new PublicCourseSourceKeys(), JsonMapper.builder().build(), TransactionOperations.withoutTransaction(),
            executor, clock, UUID::randomUUID);
    }

    private static PublicCourseProperties properties(String key, String... codes) {
        return new PublicCourseProperties(true, null, key, 1000, null, Duration.ZERO, null, List.of(codes));
    }

    private static Map<String, String> row(String lectureName, String capacity) {
        return row(SUSEONG, lectureName, capacity);
    }

    private static Map<String, String> row(String insttCode, String lectureName, String capacity) {
        Map<String, String> row = PublicCourseNormalizerTest.suseongRow();
        row.put("insttCode", insttCode);
        row.put("lctreNm", lectureName);
        row.put("psncpa", capacity);
        return row;
    }

    private static final class FakeSource implements PublicCourseSource {

        private final Map<String, List<Map<String, String>>> rows = new HashMap<>();
        private final Map<String, String> failures = new HashMap<>();
        private final List<String> calls = new ArrayList<>();
        private final Map<String, Integer> emptyFrom = new HashMap<>();
        private final Map<String, Integer> totals = new HashMap<>();

        void emptyFromPage(String insttCode, int pageNo) {
            emptyFrom.put(insttCode, pageNo);
        }

        void overrideTotal(String insttCode, int total) {
            totals.put(insttCode, total);
        }

        @SafeVarargs
        final void put(String insttCode, Map<String, String>... items) {
            failures.remove(insttCode);
            emptyFrom.remove(insttCode);
            totals.remove(insttCode);
            rows.put(insttCode, List.of(items));
        }

        void fail(String insttCode, String code) {
            failures.put(insttCode, code);
        }

        @Override
        public Page fetch(String insttCode, int pageNo, int numOfRows) {
            calls.add(insttCode + "#" + pageNo);
            if (failures.containsKey(insttCode)) {
                throw new PublicCourseSourceException(failures.get(insttCode));
            }
            List<Map<String, String>> all = rows.getOrDefault(insttCode, List.of());
            int total = totals.getOrDefault(insttCode, all.size());
            if (pageNo >= emptyFrom.getOrDefault(insttCode, Integer.MAX_VALUE)) {
                return new Page(List.of(), 0);
            }
            int from = Math.min(all.size(), (pageNo - 1) * numOfRows);
            int to = Math.min(all.size(), from + numOfRows);
            return new Page(all.subList(from, to), total);
        }
    }

    private static final class FakeCourses implements PublicCourseRepository {

        private final Map<String, PublicCourse> byKey = new LinkedHashMap<>();
        private String failOn;

        PublicCourse named(String lectureName) {
            return byKey.values().stream()
                .filter(course -> course.course().lectureName().equals(lectureName))
                .findFirst().orElseThrow();
        }

        @Override
        public Map<String, Stored> findStoredByInsttCode(String insttCode) {
            Map<String, Stored> stored = new HashMap<>();
            byKey.values().stream()
                .filter(course -> course.course().insttCode().equals(insttCode))
                .forEach(course -> stored.put(course.sourceKey(),
                    new Stored(course.id(), course.rawHash(), course.active())));
            return stored;
        }

        @Override
        public void insert(PublicCourse course) {
            if (course.course().lectureName().equals(failOn)) {
                throw new org.springframework.dao.DataIntegrityViolationException("simulated");
            }
            byKey.put(course.sourceKey(), course);
        }

        @Override
        public void replace(PublicCourse course) {
            PublicCourse existing = byKey.get(course.sourceKey());
            byKey.put(course.sourceKey(), new PublicCourse(existing.id(), course.sourceKey(), course.rawHash(),
                course.course(), course.rawJson(), true, existing.firstSeenAt(), course.lastSeenAt(), null));
        }

        @Override
        public int touch(List<String> sourceKeys, Instant seenAt) {
            sourceKeys.forEach(key -> {
                PublicCourse existing = byKey.get(key);
                byKey.put(key, new PublicCourse(existing.id(), key, existing.rawHash(), existing.course(),
                    existing.rawJson(), true, existing.firstSeenAt(), seenAt, null));
            });
            return sourceKeys.size();
        }

        @Override
        public int deactivateUnseen(String insttCode, Instant seenAt) {
            List<PublicCourse> unseen = byKey.values().stream()
                .filter(course -> course.course().insttCode().equals(insttCode))
                .filter(course -> course.active() && course.lastSeenAt().isBefore(seenAt))
                .toList();
            unseen.forEach(course -> byKey.put(course.sourceKey(), new PublicCourse(course.id(), course.sourceKey(),
                course.rawHash(), course.course(), course.rawJson(), false, course.firstSeenAt(),
                course.lastSeenAt(), seenAt)));
            return unseen.size();
        }

        @Override
        public Optional<PublicCourse> findById(UUID id) {
            return byKey.values().stream().filter(course -> course.id().equals(id)).findFirst();
        }

        @Override
        public List<PublicCourse> search(Query query, int limit, long offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count(Query query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Long> countByRegion(Query query) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeRuns implements PublicCourseSyncRunRepository {

        private final Map<UUID, PublicCourseSyncRun> byId = new LinkedHashMap<>();
        private boolean blockNext;
        private Instant closedBefore;

        @Override
        public void insert(PublicCourseSyncRun run) {
            if (blockNext) {
                throw new DuplicateKeyException("uq_tb_public_course_sync_runs_running");
            }
            byId.put(run.id(), run);
        }

        @Override
        public void update(PublicCourseSyncRun run) {
            byId.put(run.id(), run);
        }

        @Override
        public Optional<PublicCourseSyncRun> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<PublicCourseSyncRun> findPage(int limit, long offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long count() {
            return byId.size();
        }

        @Override
        public int closeStaleRunning(Instant startedBefore, Instant at) {
            closedBefore = startedBefore;
            return 0;
        }
    }
}
