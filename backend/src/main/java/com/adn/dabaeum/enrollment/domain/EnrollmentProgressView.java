package com.adn.dabaeum.enrollment.domain;

import java.util.UUID;

public record EnrollmentProgressView(
    UUID enrollmentId,
    UUID userId,
    String userName,
    UUID courseId,
    String courseTitle,
    long attendedCount,
    long sessionCount,
    String status
) {

    public int progressPercent() {
        if (sessionCount <= 0) {
            return 0;
        }
        long percent = Math.round(attendedCount * 100.0 / sessionCount);
        return (int) Math.min(100, Math.max(0, percent));
    }
}
