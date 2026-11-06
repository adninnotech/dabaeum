package com.adn.dabaeum.review.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseReviewViewRow(
    UUID id,
    UUID userId,
    UUID courseId,
    Integer rating,
    String content,
    Instant createdAt,
    String courseTitle,
    String userName,
    String institutionName,
    String instructorName,
    String classificationCode,
    String educationType,
    Integer sessionCount,
    String tags,
    String tuitionType,
    BigDecimal tuitionAmount,
    UUID thumbnailFileId
) {
}
