package com.adn.dabaeum.course.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 강좌 상세 설정. Course 레코드가 37개 컴포넌트가 되는 것을 막으려고 한 덩어리로 묶었다.
 * 저장은 tb_courses 의 평평한 컬럼들이고 조립은 매퍼가 한다.
 *
 * <p>컴팩트 생성자가 던지는 {@link IllegalArgumentException} 의 메시지는 항상
 * {@code "<필드명> ..."} 로 시작한다. API 계층이 메시지 첫 단어를 필드명으로 꺼내 쓰므로
 * 메시지를 바꿀 때는 이 규약을 지켜야 한다.
 */
public record CourseDetail(
    String tags,
    Set<CourseTargetAudience> targetAudiences,
    boolean instructorProfileVisible,
    CourseFeeType tuitionType,
    BigDecimal tuitionAmount,
    CourseFeeType materialFeeType,
    BigDecimal materialFeeAmount,
    boolean completionEnabled,
    CourseCompletionCriteria completionCriteria,
    Integer requiredProgressRate,
    boolean issueCertificate,
    String notes,
    String materials,
    String learningObjective,
    CourseSelectionMethod selectionMethod,
    Integer waitlistCapacity
) {

    private static final int MAX_TAGS_LENGTH = 60;
    private static final int MAX_MATERIALS_LENGTH = 500;

    public CourseDetail {
        requireLength(tags, "tags", MAX_TAGS_LENGTH);
        requireLength(materials, "materials", MAX_MATERIALS_LENGTH);
        targetAudiences = normalize(targetAudiences);
        tuitionType = Objects.requireNonNullElse(tuitionType, CourseFeeType.FREE);
        materialFeeType = Objects.requireNonNullElse(materialFeeType, CourseFeeType.FREE);
        selectionMethod = Objects.requireNonNullElse(
            selectionMethod, CourseSelectionMethod.FCFS);
        requireAmount(tuitionType, tuitionAmount, "tuitionAmount");
        requireAmount(materialFeeType, materialFeeAmount, "materialFeeAmount");
        requireRate(requiredProgressRate);
        if (waitlistCapacity != null && waitlistCapacity < 0) {
            throw new IllegalArgumentException("waitlistCapacity must not be negative");
        }
    }

    public static CourseDetail empty() {
        return new CourseDetail(
            null, Set.of(), true,
            CourseFeeType.FREE, null, CourseFeeType.FREE, null,
            false, null, null, false,
            null, null, null,
            CourseSelectionMethod.FCFS, null);
    }

    public CourseDetail withTags(String value) {
        return new CourseDetail(value, targetAudiences, instructorProfileVisible,
            tuitionType, tuitionAmount, materialFeeType, materialFeeAmount,
            completionEnabled, completionCriteria, requiredProgressRate, issueCertificate,
            notes, materials, learningObjective, selectionMethod, waitlistCapacity);
    }

    public CourseDetail withTargetAudiences(Collection<CourseTargetAudience> values) {
        return new CourseDetail(tags, normalize(values), instructorProfileVisible,
            tuitionType, tuitionAmount, materialFeeType, materialFeeAmount,
            completionEnabled, completionCriteria, requiredProgressRate, issueCertificate,
            notes, materials, learningObjective, selectionMethod, waitlistCapacity);
    }

    public CourseDetail withTuition(CourseFeeType type, BigDecimal amount) {
        return new CourseDetail(tags, targetAudiences, instructorProfileVisible,
            type, amount, materialFeeType, materialFeeAmount,
            completionEnabled, completionCriteria, requiredProgressRate, issueCertificate,
            notes, materials, learningObjective, selectionMethod, waitlistCapacity);
    }

    public CourseDetail withMaterialFee(CourseFeeType type, BigDecimal amount) {
        return new CourseDetail(tags, targetAudiences, instructorProfileVisible,
            tuitionType, tuitionAmount, type, amount,
            completionEnabled, completionCriteria, requiredProgressRate, issueCertificate,
            notes, materials, learningObjective, selectionMethod, waitlistCapacity);
    }

    public CourseDetail withCompletion(
        boolean enabled,
        CourseCompletionCriteria criteria,
        Integer rate,
        boolean certificate
    ) {
        return new CourseDetail(tags, targetAudiences, instructorProfileVisible,
            tuitionType, tuitionAmount, materialFeeType, materialFeeAmount,
            enabled, criteria, rate, certificate,
            notes, materials, learningObjective, selectionMethod, waitlistCapacity);
    }

    /** 쉼표 구분 문자열로 직렬화한다. 비어 있으면 null 을 준다(컬럼도 nullable). */
    public String targetAudiencesAsText() {
        return targetAudiences.isEmpty()
            ? null
            : targetAudiences.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    public static Set<CourseTargetAudience> parseTargetAudiences(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(CourseTargetAudience::valueOf)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<CourseTargetAudience> normalize(
        Collection<CourseTargetAudience> values
    ) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(EnumSet.copyOf(values));
    }

    private static void requireAmount(
        CourseFeeType type, BigDecimal amount, String field
    ) {
        if (type == CourseFeeType.PAID && amount == null) {
            throw new IllegalArgumentException(field + " is required when type is PAID");
        }
        if (amount != null && amount.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }

    private static void requireRate(Integer rate) {
        if (rate != null && (rate < 0 || rate > 100)) {
            throw new IllegalArgumentException(
                "requiredProgressRate must be between 0 and 100");
        }
    }

    private static void requireLength(String value, String field, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(
                field + " must not exceed " + max + " characters");
        }
    }
}
