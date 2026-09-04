package com.adn.dabaeum.course.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CourseRow(
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
    UUID thumbnailFileId
) {

    /** 썸네일 컬럼이 없는 행. */
    public CourseRow(
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
        Instant deletedAt
    ) {
        this(id, institutionId, courseCode, title, description, category, educationType,
            startDate, endDate, recruitStartDate, recruitEndDate, capacity, location,
            onlineUrl, creditBankEligible, creditValue, status, createdAt, updatedAt,
            deletedAt, null);
    }
}
