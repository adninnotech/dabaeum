package com.adn.dabaeum.publiccourse.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 공공데이터 강좌 수집 실행 이력 (설계서 §3.5, §4.2). 불변이며 상태 변경은 새 값을 돌려준다. */
public record PublicCourseSyncRun(
    UUID id,
    Trigger trigger,
    UUID requestedBy,
    Status status,
    Instant startedAt,
    Instant finishedAt,
    Counts counts,
    List<InstitutionResult> institutionResults,
    String errorCode
) {

    public PublicCourseSyncRun {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(counts, "counts");
        institutionResults = List.copyOf(institutionResults);
        if ((status == Status.RUNNING) != (finishedAt == null)) {
            throw new IllegalArgumentException("finishedAt must be set exactly when the run is finished");
        }
    }

    public static PublicCourseSyncRun start(UUID id, Trigger trigger, UUID requestedBy, Instant startedAt) {
        return new PublicCourseSyncRun(id, trigger, requestedBy, Status.RUNNING, startedAt, null, Counts.ZERO,
            List.of(), null);
    }

    /** 기관 결과로 최종 상태를 정한다. 기관 결과가 없거나 모두 실패면 FAILED. */
    public PublicCourseSyncRun finish(Counts finalCounts, List<InstitutionResult> results, Instant at) {
        Status finalStatus = statusOf(results);
        String code = finalStatus == Status.FAILED ? "ALL_INSTITUTIONS_FAILED" : null;
        return new PublicCourseSyncRun(id, trigger, requestedBy, finalStatus, startedAt, at, finalCounts, results,
            code);
    }

    /** 시작 전 오류(서비스키 없음 등) 또는 중단된 실행을 닫는다. */
    public PublicCourseSyncRun fail(String code, Instant at) {
        return new PublicCourseSyncRun(id, trigger, requestedBy, Status.FAILED, startedAt, at, counts,
            institutionResults, Objects.requireNonNull(code, "code"));
    }

    private static Status statusOf(List<InstitutionResult> results) {
        if (results.isEmpty()
            || results.stream().allMatch(result -> result.status() == InstitutionStatus.FAILED)) {
            return Status.FAILED;
        }
        boolean allSucceeded = results.stream().allMatch(result -> result.status() == InstitutionStatus.SUCCEEDED);
        return allSucceeded ? Status.SUCCEEDED : Status.PARTIAL;
    }

    public enum Trigger { SCHEDULED, MANUAL }

    public enum Status { RUNNING, SUCCEEDED, PARTIAL, FAILED }

    public enum InstitutionStatus { SUCCEEDED, FAILED, EMPTY }

    public record InstitutionResult(
        String insttCode, String insttName, InstitutionStatus status, int fetched, String errorCode) {

        public InstitutionResult {
            Objects.requireNonNull(insttCode, "insttCode");
            Objects.requireNonNull(status, "status");
        }
    }

    public record Counts(
        int fetched, int inserted, int updated, int unchanged, int deactivated, int skipped, int parseFailures,
        int keyCollisions) {

        public static final Counts ZERO = new Counts(0, 0, 0, 0, 0, 0, 0, 0);

        public Counts plus(Counts other) {
            return new Counts(fetched + other.fetched, inserted + other.inserted, updated + other.updated,
                unchanged + other.unchanged, deactivated + other.deactivated, skipped + other.skipped,
                parseFailures + other.parseFailures, keyCollisions + other.keyCollisions);
        }
    }
}
