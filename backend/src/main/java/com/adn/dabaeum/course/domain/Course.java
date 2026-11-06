package com.adn.dabaeum.course.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Course(
    UUID id,
    UUID institutionId,
    String courseCode,
    String title,
    String description,
    String classificationCode,
    CourseEducationType educationType,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate recruitStartDate,
    LocalDate recruitEndDate,
    Integer capacity,
    String location,
    String onlineUrl,
    boolean creditBankEligible,
    BigDecimal creditValue,
    CourseStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    UUID thumbnailFileId,
    CourseDetail detail
) {

    public Course {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(institutionId, "institutionId");
        requireText(courseCode, "courseCode", 50);
        requireText(title, "title", 200);
        requireLength(description, "description", Integer.MAX_VALUE);
        requireLength(classificationCode, "classificationCode", 11);
        Objects.requireNonNull(educationType, "educationType");
        if (status != CourseStatus.DRAFT && status != CourseStatus.CANCELLED
            && (startDate == null || endDate == null || capacity == null)) {
            throw new IllegalArgumentException(
                "startDate, endDate and capacity are required unless status is DRAFT or CANCELLED");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
        if (recruitStartDate != null
            && recruitEndDate != null
            && recruitStartDate.isAfter(recruitEndDate)) {
            throw new IllegalArgumentException(
                "recruitStartDate must not be after recruitEndDate"
            );
        }
        if (capacity != null && capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        requireLength(location, "location", 500);
        requireLength(onlineUrl, "onlineUrl", 1000);
        if (creditValue != null
            && (creditValue.compareTo(new BigDecimal("0.01")) < 0
                || creditValue.compareTo(new BigDecimal("999.99")) > 0)) {
            throw new IllegalArgumentException(
                "creditValue must be between 0.01 and 999.99"
            );
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        detail = Objects.requireNonNullElseGet(detail, CourseDetail::empty);
    }

    /** 썸네일이 없는 과정. */
    public Course(
        UUID id,
        UUID institutionId,
        String courseCode,
        String title,
        String description,
        String classificationCode,
        CourseEducationType educationType,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate recruitStartDate,
        LocalDate recruitEndDate,
        Integer capacity,
        String location,
        String onlineUrl,
        boolean creditBankEligible,
        BigDecimal creditValue,
        CourseStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
    ) {
        this(id, institutionId, courseCode, title, description, classificationCode, educationType,
            startDate, endDate, recruitStartDate, recruitEndDate, capacity, location,
            onlineUrl, creditBankEligible, creditValue, status, createdAt, updatedAt,
            deletedAt, null, null);
    }

    /** detail 만 교체한 새 Course. 21개 인자 재조립을 막는다. */
    public Course withDetail(CourseDetail newDetail) {
        return new Course(id, institutionId, courseCode, title, description, classificationCode,
            educationType, startDate, endDate, recruitStartDate, recruitEndDate,
            capacity, location, onlineUrl, creditBankEligible, creditValue, status,
            createdAt, updatedAt, deletedAt, thumbnailFileId, newDetail);
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        requireLength(value, field, maxLength);
    }

    private static void requireLength(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                field + " must not exceed " + maxLength + " characters"
            );
        }
    }
}
