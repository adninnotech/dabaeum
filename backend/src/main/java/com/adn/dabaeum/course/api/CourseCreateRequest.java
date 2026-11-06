package com.adn.dabaeum.course.api;

import com.adn.dabaeum.course.domain.CourseEducationType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public final class CourseCreateRequest {

    @NotNull
    private UUID institutionId;

    @Size(max = 50)
    private String courseCode;

    @NotBlank
    @Size(max = 60)
    private String title;

    private String description;

    @Size(max = 11)
    private String classificationCode;

    @NotNull
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

    private UUID thumbnailFileId;

    @Valid
    private CourseDetailPayload detail;

    private boolean creditBankEligiblePresent;

    public CourseCreateRequest() {
    }

    public UUID institutionId() {
        return institutionId;
    }

    public String courseCode() {
        return courseCode;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String classificationCode() {
        return classificationCode;
    }

    public CourseEducationType educationType() {
        return educationType;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public LocalDate recruitStartDate() {
        return recruitStartDate;
    }

    public LocalDate recruitEndDate() {
        return recruitEndDate;
    }

    public Integer capacity() {
        return capacity;
    }

    public String location() {
        return location;
    }

    public String onlineUrl() {
        return onlineUrl;
    }

    public Boolean creditBankEligible() {
        return creditBankEligiblePresent ? creditBankEligible : Boolean.FALSE;
    }

    public BigDecimal creditValue() {
        return creditValue;
    }

    public UUID thumbnailFileId() {
        return thumbnailFileId;
    }

    public CourseDetailPayload detail() {
        return detail;
    }

    @JsonSetter("institutionId")
    public void setInstitutionId(UUID institutionId) {
        this.institutionId = institutionId;
    }

    @JsonSetter("courseCode")
    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
    }

    @JsonSetter("classificationCode")
    public void setCategory(String classificationCode) {
        this.classificationCode = classificationCode;
    }

    @JsonSetter("educationType")
    public void setEducationType(CourseEducationType educationType) {
        this.educationType = educationType;
    }

    @JsonSetter("startDate")
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    @JsonSetter("endDate")
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    @JsonSetter("recruitStartDate")
    public void setRecruitStartDate(LocalDate recruitStartDate) {
        this.recruitStartDate = recruitStartDate;
    }

    @JsonSetter("recruitEndDate")
    public void setRecruitEndDate(LocalDate recruitEndDate) {
        this.recruitEndDate = recruitEndDate;
    }

    @JsonSetter("capacity")
    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    @JsonSetter("location")
    public void setLocation(String location) {
        this.location = location;
    }

    @JsonSetter("onlineUrl")
    public void setOnlineUrl(String onlineUrl) {
        this.onlineUrl = onlineUrl;
    }

    @JsonSetter("creditBankEligible")
    public void setCreditBankEligible(Boolean creditBankEligible) {
        this.creditBankEligiblePresent = true;
        this.creditBankEligible = creditBankEligible;
    }

    @JsonSetter("creditValue")
    public void setCreditValue(BigDecimal creditValue) {
        this.creditValue = creditValue;
    }

    @JsonSetter("thumbnailFileId")
    public void setThumbnailFileId(UUID thumbnailFileId) {
        this.thumbnailFileId = thumbnailFileId;
    }

    @JsonSetter("detail")
    public void setDetail(CourseDetailPayload detail) {
        this.detail = detail;
    }

    @JsonIgnore
    @AssertTrue(message = "startDate must not be after endDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }

    @JsonIgnore
    @AssertTrue(message = "recruitStartDate must not be after recruitEndDate")
    public boolean isRecruitmentDateRangeValid() {
        return recruitStartDate == null
            || recruitEndDate == null
            || !recruitStartDate.isAfter(recruitEndDate);
    }

    @JsonIgnore
    @AssertTrue(message = "creditBankEligible must not be null")
    public boolean isCreditBankEligibleValueValid() {
        return !creditBankEligiblePresent || creditBankEligible != null;
    }
}
