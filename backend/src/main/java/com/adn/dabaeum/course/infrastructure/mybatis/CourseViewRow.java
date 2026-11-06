package com.adn.dabaeum.course.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@link CourseRow} 와 같은 37개 컬럼에 승인된 신청자 수(enrolledCount)를 더한 읽기 전용 투영. */
public record CourseViewRow(
    UUID id,
    UUID institutionId,
    String courseCode,
    String title,
    String description,
    String classificationCode,
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
    String tags,
    String targetAudiences,
    Boolean instructorProfileVisible,
    String tuitionType,
    BigDecimal tuitionAmount,
    String materialFeeType,
    BigDecimal materialFeeAmount,
    Boolean completionEnabled,
    String completionCriteria,
    Integer requiredProgressRate,
    Boolean issueCertificate,
    String notes,
    String materials,
    String learningObjective,
    String selectionMethod,
    Integer waitlistCapacity,
    Long enrolledCount,
    String institutionName,
    String mainInstructorName
) {

    /** enrolledCount 를 뺀 나머지 37개 컬럼을 {@link CourseRow} 로 옮긴다. */
    public CourseRow toCourseRow() {
        return new CourseRow(
            id, institutionId, courseCode, title, description, classificationCode, educationType,
            startDate, endDate, recruitStartDate, recruitEndDate, capacity, location,
            onlineUrl, creditBankEligible, creditValue, status, createdAt, updatedAt,
            deletedAt, thumbnailFileId, tags, targetAudiences, instructorProfileVisible,
            tuitionType, tuitionAmount, materialFeeType, materialFeeAmount,
            completionEnabled, completionCriteria, requiredProgressRate, issueCertificate,
            notes, materials, learningObjective, selectionMethod, waitlistCapacity);
    }
}
