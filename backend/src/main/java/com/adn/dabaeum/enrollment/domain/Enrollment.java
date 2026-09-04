package com.adn.dabaeum.enrollment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Enrollment(
    UUID id,
    UUID courseId,
    UUID userId,
    UUID appliedBy,
    EnrollmentApplicationType applicationType,
    EnrollmentStatus status,
    Instant appliedAt,
    Instant approvedAt,
    Instant rejectedAt,
    Instant cancelledAt,
    Instant withdrawnAt,
    String rejectionReason,
    String cancellationReason,
    Instant createdAt,
    Instant updatedAt
) {

    public Enrollment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(applicationType, "applicationType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(appliedAt, "appliedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (createdAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("createdAt must not be after updatedAt");
        }
        rejectionReason = normalizeReason(rejectionReason, "rejectionReason");
        cancellationReason = normalizeReason(cancellationReason, "cancellationReason");
        switch (status) {
            case APPLIED, WAITLISTED -> requireState(
                approvedAt == null && rejectedAt == null && cancelledAt == null
                    && withdrawnAt == null && rejectionReason == null && cancellationReason == null,
                "pending enrollment must not have transition data");
            case APPROVED -> requireState(
                approvedAt != null && rejectedAt == null && cancelledAt == null
                    && withdrawnAt == null && rejectionReason == null && cancellationReason == null,
                "approved enrollment transition data is invalid");
            case REJECTED -> requireState(
                approvedAt == null && rejectedAt != null && cancelledAt == null
                    && withdrawnAt == null && rejectionReason != null && cancellationReason == null,
                "rejected enrollment transition data is invalid");
            case CANCELLED -> requireState(
                approvedAt == null && rejectedAt == null && cancelledAt != null
                    && withdrawnAt == null && rejectionReason == null,
                "cancelled enrollment transition data is invalid");
            case WITHDRAWN -> requireState(
                approvedAt != null && rejectedAt == null && cancelledAt == null
                    && withdrawnAt != null && rejectionReason == null && cancellationReason == null,
                "withdrawn enrollment transition data is invalid");
        }
    }

    private static String normalizeReason(String value, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException(field + " must not exceed 1000 characters");
        }
        return normalized;
    }

    private static void requireState(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
