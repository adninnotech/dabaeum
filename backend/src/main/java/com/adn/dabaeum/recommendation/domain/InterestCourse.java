package com.adn.dabaeum.recommendation.domain;

import java.util.Objects;
import java.util.UUID;

/** 회원 관심 분야(강좌 분류 1단계)에 드는 모집 중 강좌. classificationName 은 그 1단계 이름이다. */
public record InterestCourse(UUID courseId, String classificationCode, String classificationName) {

    public InterestCourse {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(classificationCode, "classificationCode");
    }
}
