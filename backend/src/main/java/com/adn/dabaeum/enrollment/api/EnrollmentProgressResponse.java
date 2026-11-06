package com.adn.dabaeum.enrollment.api;

import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentProgressResponse(
    UUID enrollmentId,
    UUID userId,
    UUID courseId,
    String courseTitle,
    long attendedCount,
    long sessionCount,
    int progressPercent,
    /** 출석률(0~100). 학습자 출결 요약과 같은 규칙. progressPercent(학습 진도)와 다르다. */
    java.math.BigDecimal attendanceRate,
    /** 마지막 출석(PRESENT·LATE) 시각. 출석 기록이 없으면 null. */
    Instant lastStudiedAt,
    String status,
    @JsonUnwrapped EnrolleeProfile profile
) {
}
