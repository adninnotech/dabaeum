package com.adn.dabaeum.course.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InstructorCourseViewRow(
    UUID id,
    UUID institutionId,
    String courseCode,
    String title,
    String description,
    String category,
    String educationType,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate recruitStartDate,
    LocalDate recruitEndDate,
    Integer capacity,
    String location,
    String onlineUrl,
    Boolean creditBankEligible,
    BigDecimal creditValue,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    UUID thumbnailFileId,
    String instructorRole,
    Long enrolledCount
) {
}
