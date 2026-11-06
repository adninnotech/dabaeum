package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
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
    Instant updatedAt,
    /** approvedAt·rejectedAt·cancelledAt·withdrawnAt 중 마지막으로 일어난 처리의 시각.
     * 프론트가 상태별로 분기하지 않도록 단일 필드로 제공한다(기존 세 필드는 하위호환으로 유지). */
    Instant processedAt,
    /** WAITLISTED 가 아니면 null. 목록 조회에서만 채워진다. */
    Integer waitlistPosition,
    /** 출석 대비 진행률(%). 목록 조회에서만 의미가 있고, 그 외에는 0. */
    int progressPercent,
    /** 마지막 출석(PRESENT·LATE) 시각. 목록 조회에서만 채워진다. */
    Instant lastStudiedAt,
    @JsonUnwrapped EnrolleeProfile profile
) {
}
