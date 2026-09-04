package com.adn.dabaeum.course.api;

import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CourseResponse(
    UUID id,
    UUID institutionId,
    String courseCode,
    String title,
    String description,
    String category,
    CourseEducationType educationType,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate recruitStartDate,
    LocalDate recruitEndDate,
    int capacity,
    String location,
    String onlineUrl,
    boolean creditBankEligible,
    BigDecimal creditValue,
    CourseStatus status,
    Instant createdAt,
    Instant updatedAt,
    UUID thumbnailFileId
) {
}
