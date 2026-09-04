package com.adn.dabaeum.enrollment.api;

import java.util.UUID;

public record EnrollmentProgressResponse(
    UUID enrollmentId,
    UUID userId,
    String userName,
    UUID courseId,
    String courseTitle,
    long attendedCount,
    long sessionCount,
    int progressPercent,
    String status
) {
}
