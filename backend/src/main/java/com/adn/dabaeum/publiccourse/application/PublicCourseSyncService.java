package com.adn.dabaeum.publiccourse.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.publiccourse.domain.PublicCourse;
import com.adn.dabaeum.publiccourse.domain.PublicCourseRepository;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun.Counts;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun.InstitutionResult;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun.InstitutionStatus;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRun.Trigger;
import com.adn.dabaeum.publiccourse.domain.PublicCourseSyncRunRepository;
import com.adn.dabaeum.publiccourse.application.port.PublicCourseSource;
import com.adn.dabaeum.publiccourse.application.port.PublicCourseSourceException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.ObjectMapper;

/**
 * 공공데이터 강좌 수집 (설계서 §3).
 *
 * <p>실행 이력을 RUNNING 으로 먼저 넣어 동시 실행을 막고(DB 유일 인덱스), 실제 수집은 {@code executor} 에서 돈다.
 * 기관마다 모든 페이지를 받은 뒤 한 트랜잭션으로 반영한다. 호출이 실패했거나 0건인 기관은 비활성 처리를 건너뛴다.
 */
public class PublicCourseSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicCourseSyncService.class);

    /** 한 기관이 이보다 많다고 하면 응답 오류로 본다(대구 최대 기관이 약 2천 건). 페이지 호출 수의 상한이기도 하다. */
    static final int MAX_INSTITUTION_ROWS = 100_000;

    private final PublicCourseSource source;
    private final PublicCourseRepository courses;
    private final PublicCourseSyncRunRepository runs;
    private final PublicCourseProperties properties;
    private final PublicCourseNormalizer normalizer;
    private final PublicCourseSourceKeys keys;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactions;
    private final Executor executor;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public PublicCourseSyncService(
        PublicCourseSource source,
        PublicCourseRepository courses,
        PublicCourseSyncRunRepository runs,
        PublicCourseProperties properties,
        PublicCourseNormalizer normalizer,
        PublicCourseSourceKeys keys,
        ObjectMapper objectMapper,
        TransactionOperations transactions,
        Executor executor,
        Clock clock,
        Supplier<UUID> ids
    ) {
        this.source = Objects.requireNonNull(source, "source");
        this.courses = Objects.requireNonNull(courses, "courses");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /** 자동 수집. 이미 돌고 있으면 건너뛴다(빈 값). */
    public Optional<UUID> startScheduled() {
        try {
            return Optional.of(start(Trigger.SCHEDULED, null));
        } catch (DuplicateKeyException exception) {
            LOGGER.info("PUBLIC_COURSE_SYNC_SKIPPED reason=running");
            return Optional.empty();
        }
    }

    /** 플랫폼 관리자 수동 수집. 이미 돌고 있으면 409. */
    public UUID startManual(UUID requestedBy) {
        Objects.requireNonNull(requestedBy, "requestedBy");
        try {
            return start(Trigger.MANUAL, requestedBy);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.PUBLIC_COURSE_SYNC_RUNNING,
                "Public course sync is already running");
        }
    }

    private UUID start(Trigger trigger, UUID requestedBy) {
        Instant now = clock.instant();
        int closed = runs.closeStaleRunning(now.minus(properties.staleRunAfter()), now);
        if (closed > 0) {
            LOGGER.warn("PUBLIC_COURSE_SYNC_INTERRUPTED_CLOSED count={}", closed);
        }
        PublicCourseSyncRun run = PublicCourseSyncRun.start(ids.get(), trigger, requestedBy, now);
        runs.insert(run);
        try {
            executor.execute(() -> execute(run));
        } catch (RuntimeException exception) {
            LOGGER.warn("PUBLIC_COURSE_SYNC_FAILED runId={} code=EXECUTOR_REJECTED type={}", run.id(),
                exception.getClass().getName());
            runs.update(run.fail("EXECUTOR_REJECTED", clock.instant()));
        }
        return run.id();
    }

    void execute(PublicCourseSyncRun run) {
        try {
            if (!properties.hasServiceKey()) {
                runs.update(run.fail("SERVICE_KEY_MISSING", clock.instant()));
                LOGGER.warn("PUBLIC_COURSE_SYNC_FAILED runId={} code=SERVICE_KEY_MISSING", run.id());
                return;
            }
            Counts total = Counts.ZERO;
            List<InstitutionResult> results = new ArrayList<>();
            for (String insttCode : properties.institutionCodes()) {
                InstitutionOutcome outcome = syncInstitution(insttCode);
                total = total.plus(outcome.counts());
                results.add(outcome.result());
            }
            PublicCourseSyncRun finished = run.finish(total, results, clock.instant());
            runs.update(finished);
            LOGGER.info("PUBLIC_COURSE_SYNC_FINISHED runId={} status={} counts={}", run.id(), finished.status(),
                total);
        } catch (SyncInterruptedException exception) {
            LOGGER.warn("PUBLIC_COURSE_SYNC_FAILED runId={} code=INTERRUPTED", run.id());
            runs.update(run.fail("INTERRUPTED", clock.instant()));
        } catch (RuntimeException exception) {
            LOGGER.warn("PUBLIC_COURSE_SYNC_FAILED runId={} type={}", run.id(), exception.getClass().getName(),
                exception);
            runs.update(run.fail("UNEXPECTED_ERROR", clock.instant()));
        }
    }

    private InstitutionOutcome syncInstitution(String insttCode) {
        List<Map<String, String>> rows;
        try {
            rows = fetchAll(insttCode);
        } catch (PublicCourseSourceException exception) {
            LOGGER.warn("PUBLIC_COURSE_SYNC_INSTITUTION_FAILED insttCode={} code={}", insttCode, exception.getCode());
            return InstitutionOutcome.of(
                new InstitutionResult(insttCode, null, InstitutionStatus.FAILED, 0, exception.getCode()), Counts.ZERO);
        }
        if (rows.isEmpty()) {
            LOGGER.warn("PUBLIC_COURSE_SYNC_INSTITUTION_EMPTY insttCode={}", insttCode);
            return InstitutionOutcome.of(new InstitutionResult(insttCode, null, InstitutionStatus.EMPTY, 0, null),
                Counts.ZERO);
        }
        Counts counts;
        try {
            counts = transactions.execute(status -> apply(insttCode, rows));
        } catch (SyncInterruptedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 기관 하나의 저장 실패가 다른 기관의 수집을 막지 않는다. 트랜잭션은 롤백됐다.
            LOGGER.warn("PUBLIC_COURSE_SYNC_INSTITUTION_FAILED insttCode={} code=DB_ERROR type={}", insttCode,
                exception.getClass().getName(), exception);
            return InstitutionOutcome.of(
                new InstitutionResult(insttCode, null, InstitutionStatus.FAILED, rows.size(), "DB_ERROR"),
                Counts.ZERO);
        }
        String insttName = rows.getFirst().get("insttNm");
        return InstitutionOutcome.of(
            new InstitutionResult(insttCode, insttName, InstitutionStatus.SUCCEEDED, rows.size(), null), counts);
    }

    /**
     * 1페이지의 totalCount 만큼 모두 받아야 성공이다. 뒤 페이지가 비거나 03(데이터 없음)으로 오면 일부만 받은 것이므로
     * 실패로 보고 그 기관의 비활성 처리를 하지 않는다(설계서 §3.4).
     */
    private List<Map<String, String>> fetchAll(String insttCode) {
        int pageSize = properties.pageSize();
        PublicCourseSource.Page first = source.fetch(insttCode, 1, pageSize);
        int expected = first.totalCount();
        if (expected < 0 || expected > MAX_INSTITUTION_ROWS) {
            throw new PublicCourseSourceException("PUBLIC_DATA_INVALID_RESPONSE");
        }
        List<Map<String, String>> rows = new ArrayList<>(first.items());
        int pages = (expected + pageSize - 1) / pageSize;
        for (int pageNo = 2; pageNo <= pages; pageNo++) {
            pause();
            List<Map<String, String>> items = source.fetch(insttCode, pageNo, pageSize).items();
            if (items.isEmpty()) {
                break;
            }
            rows.addAll(items);
        }
        pause();
        if (rows.size() < expected) {
            throw new PublicCourseSourceException("PUBLIC_DATA_INCOMPLETE");
        }
        return List.copyOf(rows);
    }

    private Counts apply(String insttCode, List<Map<String, String>> rows) {
        Instant seenAt = clock.instant();
        Map<String, PublicCourseRepository.Stored> stored = courses.findStoredByInsttCode(insttCode);
        List<Map<String, String>> owned = new ArrayList<>(rows.size());
        int skipped = 0;
        for (Map<String, String> row : rows) {
            Map<String, String> clean = PublicCourseRows.sanitize(row);
            String rowCode = clean.get("insttCode");
            if (rowCode == null || rowCode.isBlank()) {
                clean.put("insttCode", insttCode);
            } else if (!rowCode.strip().equals(insttCode)) {
                skipped++;
                continue;
            }
            owned.add(clean);
        }
        PublicCourseSourceKeys.Assignment assignment = keys.assign(owned);
        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int parseFailures = 0;
        List<String> untouched = new ArrayList<>();
        for (PublicCourseSourceKeys.KeyedRow keyed : assignment.rows()) {
            PublicCourseNormalizer.Result result = normalizer.normalize(keyed.row());
            if (result.skipped()) {
                skipped++;
                continue;
            }
            parseFailures += result.parseFailures();
            String rawHash = keys.rawHash(keyed.row());
            PublicCourseRepository.Stored existing = stored.get(keyed.sourceKey());
            if (existing == null) {
                courses.insert(new PublicCourse(ids.get(), keyed.sourceKey(), rawHash, result.course(),
                    json(keyed.row()), true, seenAt, seenAt, null));
                inserted++;
            } else if (!existing.rawHash().equals(rawHash)) {
                courses.replace(new PublicCourse(existing.id(), keyed.sourceKey(), rawHash, result.course(),
                    json(keyed.row()), true, seenAt, seenAt, null));
                updated++;
            } else {
                untouched.add(keyed.sourceKey());
                if (existing.active()) {
                    unchanged++;
                } else {
                    updated++;
                }
            }
        }
        if (!untouched.isEmpty()) {
            courses.touch(untouched, seenAt);
        }
        int deactivated = courses.deactivateUnseen(insttCode, seenAt);
        return new Counts(rows.size(), inserted, updated, unchanged, deactivated, skipped, parseFailures,
            assignment.collisionGroups());
    }

    private String json(Map<String, String> row) {
        return objectMapper.writeValueAsString(row);
    }

    private void pause() {
        Duration pause = properties.requestPause();
        if (pause.isZero()) {
            return;
        }
        try {
            Thread.sleep(pause);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SyncInterruptedException();
        }
    }

    /** 스레드가 인터럽트되면 다음 기관으로 넘어가지 않고 실행 전체를 멈춘다. */
    private static final class SyncInterruptedException extends RuntimeException {

        SyncInterruptedException() {
            super("public course sync interrupted", null, false, false);
        }
    }

    private record InstitutionOutcome(InstitutionResult result, Counts counts) {

        static InstitutionOutcome of(InstitutionResult result, Counts counts) {
            return new InstitutionOutcome(result, counts);
        }
    }
}
