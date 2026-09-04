package com.adn.dabaeum.instructor.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InstructorApplication(
    UUID id,
    UUID userId,
    UUID institutionId,
    InstructorApplicationStatus status,
    String applicationMessage,
    String rejectionReason,
    UUID reviewedBy,
    Instant appliedAt,
    Instant reviewedAt,
    Instant createdAt,
    Instant updatedAt
) {

    public InstructorApplication {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(institutionId, "institutionId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(appliedAt, "appliedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        applicationMessage = normalizeOptional(applicationMessage, "applicationMessage");
        rejectionReason = normalizeOptional(rejectionReason, "rejectionReason");
        if (createdAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("createdAt must not be after updatedAt");
        }
        switch (status) {
            case PENDING -> requireState(
                reviewedBy == null && reviewedAt == null && rejectionReason == null,
                "pending application must not have review data"
            );
            case APPROVED -> requireState(
                reviewedBy != null && reviewedAt != null && rejectionReason == null,
                "approved application review data is invalid"
            );
            case REJECTED -> requireState(
                reviewedBy != null && reviewedAt != null && rejectionReason != null,
                "rejected application review data is invalid"
            );
        }
    }

    private static String normalizeOptional(String value, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
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
