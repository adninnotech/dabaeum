package com.adn.dabaeum.interest.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseInterestViewRow(
    UUID id,
    UUID courseId,
    Instant createdAt,
    String courseTitle,
    String institutionName,
    String classificationCode,
    String educationType,
    String courseStatus,
    String instructorName,
    Integer sessionCount,
    String tags,
    String tuitionType,
    BigDecimal tuitionAmount,
    UUID thumbnailFileId
) {
}
