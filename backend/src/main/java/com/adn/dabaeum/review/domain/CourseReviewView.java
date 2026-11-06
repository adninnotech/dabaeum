package com.adn.dabaeum.review.domain;

import com.adn.dabaeum.course.domain.CourseFeeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseReviewView(
    UUID id,
    UUID userId,
    UUID courseId,
    int rating,
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
    CourseFeeType tuitionType,
    BigDecimal tuitionAmount,
    UUID thumbnailFileId
) {
}
