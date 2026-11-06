package com.adn.dabaeum.recommendation.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * 기준 강좌와 분류가 비슷한 후보 강좌 (설계 §3.2). matchLevel 3 소분류, 2 중분류, 1 대분류 일치.
 * matched* 는 일치한 단계의 분류 코드·이름, source* 는 그 일치를 만든 기준 강좌다.
 */
public record SimilarCourse(
    UUID courseId,
    int matchLevel,
    String matchedClassificationCode,
    String matchedClassificationName,
    UUID sourceCourseId,
    String sourceCourseTitle,
    ReferenceSignal sourceSignal
) {

    public SimilarCourse {
        Objects.requireNonNull(courseId, "courseId");
        if (matchLevel < 1 || matchLevel > 3) {
            throw new IllegalArgumentException("matchLevel must be 1..3");
        }
        Objects.requireNonNull(sourceCourseId, "sourceCourseId");
        Objects.requireNonNull(sourceSignal, "sourceSignal");
    }
}
