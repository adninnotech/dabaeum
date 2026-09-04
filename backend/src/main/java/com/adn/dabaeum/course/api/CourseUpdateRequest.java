package com.adn.dabaeum.course.api;

import com.adn.dabaeum.course.application.CourseUpdateField;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public final class CourseUpdateRequest {

    @Size(max = 50)
    private String courseCode;
    @Size(max = 200)
    private String title;
    private String description;
    @Size(max = 100)
    private String category;
    private CourseEducationType educationType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate recruitStartDate;
    private LocalDate recruitEndDate;
    @Min(1)
    private Integer capacity;
    @Size(max = 500)
    private String location;
    @Size(max = 1000)
    private String onlineUrl;
    private Boolean creditBankEligible;
    @DecimalMin("0.01")
    @DecimalMax("999.99")
    private BigDecimal creditValue;
    private CourseStatus status;
    private UUID thumbnailFileId;

    private boolean courseCodePresent;
    private boolean titlePresent;
    private boolean descriptionPresent;
    private boolean categoryPresent;
    private boolean educationTypePresent;
    private boolean startDatePresent;
    private boolean endDatePresent;
    private boolean recruitStartDatePresent;
    private boolean recruitEndDatePresent;
    private boolean capacityPresent;
    private boolean locationPresent;
    private boolean onlineUrlPresent;
    private boolean creditBankEligiblePresent;
    private boolean creditValuePresent;
    private boolean statusPresent;
    private boolean thumbnailFileIdPresent;

    public CourseUpdateRequest() {
    }

    @JsonSetter("courseCode")
    public void setCourseCode(String value) { courseCodePresent = true; courseCode = value; }
    @JsonSetter("title")
    public void setTitle(String value) { titlePresent = true; title = value; }
    @JsonSetter("description")
    public void setDescription(String value) { descriptionPresent = true; description = value; }
    @JsonSetter("category")
    public void setCategory(String value) { categoryPresent = true; category = value; }
    @JsonSetter("educationType")
    public void setEducationType(CourseEducationType value) { educationTypePresent = true; educationType = value; }
    @JsonSetter("startDate")
    public void setStartDate(LocalDate value) { startDatePresent = true; startDate = value; }
    @JsonSetter("endDate")
    public void setEndDate(LocalDate value) { endDatePresent = true; endDate = value; }
    @JsonSetter("recruitStartDate")
    public void setRecruitStartDate(LocalDate value) { recruitStartDatePresent = true; recruitStartDate = value; }
    @JsonSetter("recruitEndDate")
    public void setRecruitEndDate(LocalDate value) { recruitEndDatePresent = true; recruitEndDate = value; }
    @JsonSetter("capacity")
    public void setCapacity(Integer value) { capacityPresent = true; capacity = value; }
    @JsonSetter("location")
    public void setLocation(String value) { locationPresent = true; location = value; }
    @JsonSetter("onlineUrl")
    public void setOnlineUrl(String value) { onlineUrlPresent = true; onlineUrl = value; }
    @JsonSetter("creditBankEligible")
    public void setCreditBankEligible(Boolean value) { creditBankEligiblePresent = true; creditBankEligible = value; }
    @JsonSetter("creditValue")
    public void setCreditValue(BigDecimal value) { creditValuePresent = true; creditValue = value; }
    @JsonSetter("status")
    public void setStatus(CourseStatus value) { statusPresent = true; status = value; }
    @JsonSetter("thumbnailFileId")
    public void setThumbnailFileId(UUID value) { thumbnailFileIdPresent = true; thumbnailFileId = value; }

    public CourseUpdateField<String> courseCodeUpdate() { return field(courseCodePresent, courseCode); }
    public CourseUpdateField<String> titleUpdate() { return field(titlePresent, title); }
    public CourseUpdateField<String> descriptionUpdate() { return field(descriptionPresent, description); }
    public CourseUpdateField<String> categoryUpdate() { return field(categoryPresent, category); }
    public CourseUpdateField<CourseEducationType> educationTypeUpdate() { return field(educationTypePresent, educationType); }
    public CourseUpdateField<LocalDate> startDateUpdate() { return field(startDatePresent, startDate); }
    public CourseUpdateField<LocalDate> endDateUpdate() { return field(endDatePresent, endDate); }
    public CourseUpdateField<LocalDate> recruitStartDateUpdate() { return field(recruitStartDatePresent, recruitStartDate); }
    public CourseUpdateField<LocalDate> recruitEndDateUpdate() { return field(recruitEndDatePresent, recruitEndDate); }
    public CourseUpdateField<Integer> capacityUpdate() { return field(capacityPresent, capacity); }
    public CourseUpdateField<String> locationUpdate() { return field(locationPresent, location); }
    public CourseUpdateField<String> onlineUrlUpdate() { return field(onlineUrlPresent, onlineUrl); }
    public CourseUpdateField<Boolean> creditBankEligibleUpdate() { return field(creditBankEligiblePresent, creditBankEligible); }
    public CourseUpdateField<BigDecimal> creditValueUpdate() { return field(creditValuePresent, creditValue); }
    public CourseUpdateField<CourseStatus> statusUpdate() { return field(statusPresent, status); }
    public CourseUpdateField<UUID> thumbnailFileIdUpdate() { return field(thumbnailFileIdPresent, thumbnailFileId); }

    @JsonIgnore
    @AssertTrue(message = "at least one field is required")
    public boolean isNotEmpty() {
        return courseCodePresent || titlePresent || descriptionPresent || categoryPresent
            || educationTypePresent || startDatePresent || endDatePresent
            || recruitStartDatePresent || recruitEndDatePresent || capacityPresent
            || locationPresent || onlineUrlPresent || creditBankEligiblePresent
            || creditValuePresent || statusPresent || thumbnailFileIdPresent;
    }

    @JsonIgnore
    @AssertTrue(message = "startDate must not be after endDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    @JsonIgnore
    @AssertTrue(message = "recruitStartDate must not be after recruitEndDate")
    public boolean isRecruitmentDateRangeValid() {
        return recruitStartDate == null || recruitEndDate == null
            || !recruitStartDate.isAfter(recruitEndDate);
    }

    private <T> CourseUpdateField<T> field(boolean present, T value) {
        return present ? CourseUpdateField.present(value) : CourseUpdateField.absent();
    }
}
