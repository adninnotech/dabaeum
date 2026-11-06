package com.adn.dabaeum.inquiry.domain;

import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.enrollment.domain.EnrolleeProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InquiryView(
    UUID id,
    UUID userId,
    UUID courseId,
    String title,
    InquiryStatus status,
    InquiryType inquiryType,
    Instant createdAt,
    String courseTitle,
    EnrolleeProfile profile,
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
