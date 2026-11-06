package com.adn.dabaeum.interest.domain;

import com.adn.dabaeum.course.domain.CourseFeeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseInterestView(
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
    CourseFeeType tuitionType,
    BigDecimal tuitionAmount,
    UUID thumbnailFileId
) {
}
