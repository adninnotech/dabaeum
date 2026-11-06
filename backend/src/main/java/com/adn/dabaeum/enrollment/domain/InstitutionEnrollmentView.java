package com.adn.dabaeum.enrollment.domain;

import java.time.Instant;
import java.util.UUID;

public record InstitutionEnrollmentView(
    UUID id,
    UUID courseId,
    UUID userId,
    EnrollmentStatus status,
    Instant appliedAt,
    Instant createdAt,
    String courseTitle,
    String userName,
    String institutionName,
    int progressPercent,
    /** 마지막 출석(PRESENT·LATE) 시각. 출석 기록이 없으면 null. */
    Instant lastStudiedAt,
    /** 마지막 처리(철회·취소·거절·승인) 시각. 처리 전이면 null. */
    Instant processedAt,
    /** 같은 강좌 대기자 중 순번(1부터). 대기(WAITLISTED)가 아니면 null. */
    Integer waitlistPosition,
    String cancellationReason
) {
}
