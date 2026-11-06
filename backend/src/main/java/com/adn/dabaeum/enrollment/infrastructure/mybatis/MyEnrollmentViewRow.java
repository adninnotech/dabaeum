package com.adn.dabaeum.enrollment.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MyEnrollmentViewRow(
    UUID id,
    UUID courseId,
    UUID userId,
    String status,
    Instant appliedAt,
    Instant createdAt,
    String courseTitle,
    String courseCode,
    String courseStatus,
    String institutionName,
    String instructorName,
    String classificationCode,
    String educationType,
    Integer sessionCount,
    String tags,
    String tuitionType,
    BigDecimal tuitionAmount,
    UUID thumbnailFileId,
    String cancellationReason
) {
}
