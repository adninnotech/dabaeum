package com.adn.dabaeum.completion.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Completion(
    UUID id,
    UUID enrollmentId,
    CompletionStatus status,
    BigDecimal attendanceRate,
    int completedMinutes,
    BigDecimal creditValue,
    Instant evaluatedAt,
    Instant completedAt,
    UUID confirmedBy,
    Instant confirmedAt,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {

    public Completion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(enrollmentId, "enrollmentId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(attendanceRate, "attendanceRate");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (attendanceRate.compareTo(BigDecimal.ZERO) < 0
            || attendanceRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("attendanceRate must be between 0 and 100");
        }
        if (completedMinutes < 0) {
            throw new IllegalArgumentException("completedMinutes must not be negative");
        }
        if (creditValue != null
            && (creditValue.compareTo(new BigDecimal("0.01")) < 0
                || creditValue.compareTo(new BigDecimal("999.99")) > 0)) {
            throw new IllegalArgumentException("creditValue must be between 0.01 and 999.99");
        }
        if (createdAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("createdAt must not be after updatedAt");
        }

        switch (status) {
            case PENDING_EVALUATION -> requireAllNull(
                evaluatedAt, completedAt, confirmedBy, confirmedAt, failureReason,
                "pending completion must not have evaluation data");
            case ELIGIBLE -> {
                requireNonNull(evaluatedAt, "eligible completion requires evaluatedAt");
                requireAllNull(completedAt, confirmedBy, confirmedAt, failureReason,
                    "eligible completion must not have confirmation or failure data");
            }
            case NOT_COMPLETED -> {
                requireNonNull(evaluatedAt, "not-completed completion requires evaluatedAt");
                requireNonBlank(failureReason, "not-completed completion requires failureReason");
                requireAllNull(completedAt, confirmedBy, confirmedAt,
                    "not-completed completion must not have confirmation data");
            }
            case COMPLETED -> {
                requireNonNull(evaluatedAt, "completed completion requires evaluatedAt");
                requireNonNull(completedAt, "completed completion requires completedAt");
                requireNonNull(confirmedBy, "completed completion requires confirmedBy");
                requireNonNull(confirmedAt, "completed completion requires confirmedAt");
                requireNull(failureReason, "completed completion must not have failureReason");
            }
            case CANCELLED -> requireAllNull(
                evaluatedAt, completedAt, confirmedBy, confirmedAt, failureReason,
                "cancelled completion must not have workflow data");
        }
        failureReason = normalizeReason(failureReason);
    }

    private static void requireAllNull(
        Object first, Object second, Object third, Object fourth, Object fifth, String message
    ) {
        if (first != null || second != null || third != null || fourth != null || fifth != null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireAllNull(
        Object first, Object second, Object third, Object fourth, String message
    ) {
        if (first != null || second != null || third != null || fourth != null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireAllNull(
        Object first, Object second, Object third, String message
    ) {
        if (first != null || second != null || third != null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNull(Object value, String message) {
        if (value != null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String normalizeReason(String value) {
        return value == null ? null : value.trim();
    }
}
