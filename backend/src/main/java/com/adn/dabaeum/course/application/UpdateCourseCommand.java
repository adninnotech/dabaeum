package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateCourseCommand(
    UUID courseId,
    CourseUpdateField<String> courseCode,
    CourseUpdateField<String> title,
    CourseUpdateField<String> description,
    CourseUpdateField<String> category,
    CourseUpdateField<CourseEducationType> educationType,
    CourseUpdateField<LocalDate> startDate,
    CourseUpdateField<LocalDate> endDate,
    CourseUpdateField<LocalDate> recruitStartDate,
    CourseUpdateField<LocalDate> recruitEndDate,
    CourseUpdateField<Integer> capacity,
    CourseUpdateField<String> location,
    CourseUpdateField<String> onlineUrl,
    CourseUpdateField<Boolean> creditBankEligible,
    CourseUpdateField<BigDecimal> creditValue,
    CourseUpdateField<CourseStatus> status,
    CourseUpdateField<UUID> thumbnailFileId
) {

    /** 썸네일을 다루지 않는 수정 명령. */
    public UpdateCourseCommand(
        UUID courseId,
        CourseUpdateField<String> courseCode,
        CourseUpdateField<String> title,
        CourseUpdateField<String> description,
        CourseUpdateField<String> category,
        CourseUpdateField<CourseEducationType> educationType,
        CourseUpdateField<LocalDate> startDate,
        CourseUpdateField<LocalDate> endDate,
        CourseUpdateField<LocalDate> recruitStartDate,
        CourseUpdateField<LocalDate> recruitEndDate,
        CourseUpdateField<Integer> capacity,
        CourseUpdateField<String> location,
        CourseUpdateField<String> onlineUrl,
        CourseUpdateField<Boolean> creditBankEligible,
        CourseUpdateField<BigDecimal> creditValue,
        CourseUpdateField<CourseStatus> status
    ) {
        this(courseId, courseCode, title, description, category, educationType,
            startDate, endDate, recruitStartDate, recruitEndDate, capacity, location,
            onlineUrl, creditBankEligible, creditValue, status, CourseUpdateField.absent());
    }

    public int presentFieldCount() {
        return count(courseCode)
            + count(title)
            + count(description)
            + count(category)
            + count(educationType)
            + count(startDate)
            + count(endDate)
            + count(recruitStartDate)
            + count(recruitEndDate)
            + count(capacity)
            + count(location)
            + count(onlineUrl)
            + count(creditBankEligible)
            + count(creditValue)
            + count(status)
            + count(thumbnailFileId);
    }

    private int count(CourseUpdateField<?> field) {
        return field != null && field.present() ? 1 : 0;
    }
}
