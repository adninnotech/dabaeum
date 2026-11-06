package com.adn.dabaeum.publiccourse.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 저장된 공공데이터 강좌 (설계서 §4.1). {@code rawJson} 은 원본 행을 직렬화한 JSON 이다. */
public record PublicCourse(
    UUID id,
    String sourceKey,
    String rawHash,
    NormalizedPublicCourse course,
    String rawJson,
    boolean active,
    Instant firstSeenAt,
    Instant lastSeenAt,
    Instant deactivatedAt
) {

    public PublicCourse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(rawHash, "rawHash");
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(rawJson, "rawJson");
        Objects.requireNonNull(firstSeenAt, "firstSeenAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        if (active != (deactivatedAt == null)) {
            throw new IllegalArgumentException("active and deactivatedAt disagree");
        }
    }
}
