package com.adn.dabaeum.institution.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InstitutionJoinApplication(
    UUID id,
    String institutionName,
    String institutionCode,
    String representativeName,
    String contactEmail,
    String contactPhone,
    String address,
    InstitutionJoinApplicationStatus status,
    String rejectionReason,
    UUID applicantUserId,
    UUID decidedBy,
    Instant decidedAt,
    UUID createdInstitutionId,
    Instant createdAt,
    Instant updatedAt
) {

    public InstitutionJoinApplication {
        Objects.requireNonNull(id, "id");
        requireText(institutionName, "institutionName", 200);
        requireText(representativeName, "representativeName", 100);
        requireText(contactEmail, "contactEmail", 255);
        requireText(contactPhone, "contactPhone", 50);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        switch (status) {
            case PENDING -> requireState(
                decidedBy == null && decidedAt == null
                    && rejectionReason == null && createdInstitutionId == null,
                "pending application must not have decision data");
            case APPROVED -> requireState(
                decidedBy != null && decidedAt != null
                    && rejectionReason == null && createdInstitutionId != null,
                "approved application decision data is invalid");
            case REJECTED -> requireState(
                decidedBy != null && decidedAt != null
                    && rejectionReason != null && createdInstitutionId == null,
                "rejected application decision data is invalid");
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                field + " must be 1.." + maxLength + " characters");
        }
    }

    private static void requireState(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
