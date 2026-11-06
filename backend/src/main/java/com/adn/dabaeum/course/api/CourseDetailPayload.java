package com.adn.dabaeum.course.api;

import com.adn.dabaeum.common.text.RichText;
import com.adn.dabaeum.course.domain.CourseCompletionCriteria;
import com.adn.dabaeum.course.domain.CourseDetail;
import com.adn.dabaeum.course.domain.CourseFeeType;
import com.adn.dabaeum.course.domain.CourseSelectionMethod;
import com.adn.dabaeum.course.domain.CourseTargetAudience;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** D-1·D-3 화면의 입력을 한 덩어리로 받는다. 요청과 응답이 같은 모양을 쓴다. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CourseDetailPayload(
    @Size(max = 60) String tags,
    List<CourseTargetAudience> targetAudiences,
    Boolean instructorProfileVisible,
    CourseFeeType tuitionType,
    @PositiveOrZero BigDecimal tuitionAmount,
    CourseFeeType materialFeeType,
    @PositiveOrZero BigDecimal materialFeeAmount,
    Boolean completionEnabled,
    CourseCompletionCriteria completionCriteria,
    @Min(0) @Max(100) Integer requiredProgressRate,
    Boolean issueCertificate,
    String notes,
    @Size(max = 500) String materials,
    String learningObjective,
    CourseSelectionMethod selectionMethod,
    @PositiveOrZero Integer waitlistCapacity
) {

    public CourseDetail toDomain() {
        if (targetAudiences != null && targetAudiences.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("targetAudiences must not contain null");
        }
        return new CourseDetail(
            tags,
            targetAudiences == null ? Set.of() : Set.copyOf(targetAudiences),
            instructorProfileVisible == null || instructorProfileVisible,
            tuitionType, tuitionAmount, materialFeeType, materialFeeAmount,
            completionEnabled != null && completionEnabled,
            completionCriteria, requiredProgressRate,
            issueCertificate != null && issueCertificate,
            RichText.sanitize(notes), materials, RichText.sanitize(learningObjective),
            selectionMethod, waitlistCapacity);
    }

    public static CourseDetailPayload from(CourseDetail detail) {
        return new CourseDetailPayload(
            detail.tags(),
            List.copyOf(detail.targetAudiences()),
            detail.instructorProfileVisible(),
            detail.tuitionType(), detail.tuitionAmount(),
            detail.materialFeeType(), detail.materialFeeAmount(),
            detail.completionEnabled(), detail.completionCriteria(),
            detail.requiredProgressRate(), detail.issueCertificate(),
            detail.notes(), detail.materials(), detail.learningObjective(),
            detail.selectionMethod(), detail.waitlistCapacity());
    }
}
