package com.adn.dabaeum.course.infrastructure.mybatis;

import com.adn.dabaeum.course.domain.CourseCompletionCriteria;
import com.adn.dabaeum.course.domain.CourseDetail;
import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.course.domain.CourseSelectionMethod;
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
    String instructorRole,
    Long enrolledCount,
    String institutionName,
    String mainInstructorName
) {

    /** 이 행의 16개 상세설정 컬럼을 CourseDetail 로 변환한다. */
    public CourseDetail toDetail() {
        return new CourseDetail(
            tags,
            CourseDetail.parseTargetAudiences(targetAudiences),
            instructorProfileVisible,
            CourseFeeType.valueOf(tuitionType),
            tuitionAmount,
            CourseFeeType.valueOf(materialFeeType),
            materialFeeAmount,
            completionEnabled,
            completionCriteria == null
                ? null : CourseCompletionCriteria.valueOf(completionCriteria),
            requiredProgressRate,
            issueCertificate,
            notes,
            materials,
            learningObjective,
            CourseSelectionMethod.valueOf(selectionMethod),
            waitlistCapacity);
    }
}
