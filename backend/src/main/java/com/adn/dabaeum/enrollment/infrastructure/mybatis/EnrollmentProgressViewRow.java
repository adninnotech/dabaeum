package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import java.util.UUID;

public record EnrollmentProgressViewRow(
    UUID enrollmentId,
    UUID userId,
    String userName,
    UUID courseId,
    String courseTitle,
    Long attendedCount,
    Long sessionCount,
    String status
) {
}
