package com.adn.dabaeum.recommendation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 사용자의 찜·신청 강좌 하나 = 기준 강좌 (설계 §3.1). 분류 코드가 유사도의 기준이다. */
public record ReferenceCourse(
    UUID courseId,
    String courseTitle,
    String classificationCode,
    ReferenceSignal signal,
    Instant signalledAt
) {

    public ReferenceCourse {
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(classificationCode, "classificationCode");
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(signalledAt, "signalledAt");
    }
}
