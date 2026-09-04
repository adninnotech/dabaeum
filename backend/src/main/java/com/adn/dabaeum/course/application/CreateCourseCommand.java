package com.adn.dabaeum.course.application;

import com.adn.dabaeum.course.domain.CourseEducationType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCourseCommand(
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
    Integer capacity,
    String location,
    String onlineUrl,
    Boolean creditBankEligible,
    BigDecimal creditValue,
    UUID thumbnailFileId
) {

    /** 썸네일 없이 생성하는 명령. */
    public CreateCourseCommand(
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
        Integer capacity,
        String location,
        String onlineUrl,
        Boolean creditBankEligible,
        BigDecimal creditValue
    ) {
        this(institutionId, courseCode, title, description, category, educationType,
            startDate, endDate, recruitStartDate, recruitEndDate, capacity, location,
            onlineUrl, creditBankEligible, creditValue, null);
    }
}
