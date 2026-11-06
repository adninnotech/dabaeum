package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

public record InstitutionEnrollmentResponse(
    UUID id,
    UUID courseId,
    UUID userId,
    EnrollmentStatus status,
    Instant appliedAt,
    Instant createdAt,
    String courseTitle,
    String userName,
    String institutionName,
    /** 출석 대비 진행률(%). */
    int progressPercent,
    /** 마지막 출석(PRESENT·LATE) 시각. */
    Instant lastStudiedAt,
    /** 마지막 처리 시각. */
    Instant processedAt,
    /** 대기 순번. 대기가 아니면 null. */
    Integer waitlistPosition,
    String cancellationReason
) {
}
