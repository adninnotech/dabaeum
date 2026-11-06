package com.adn.dabaeum.inquiry.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InquiryViewRow(
    UUID id,
    UUID userId,
    UUID courseId,
    String title,
    String status,
    String inquiryType,
    Instant createdAt,
    String courseTitle,
    String enrolleeName,
    LocalDate enrolleeBirthDate,
    String enrolleeGender,
    String enrolleeEmail,
    String enrolleePhone,
    UUID enrolleeProfileImageId,
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
