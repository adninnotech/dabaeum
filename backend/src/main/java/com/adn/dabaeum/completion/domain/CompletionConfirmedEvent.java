package com.adn.dabaeum.completion.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CompletionConfirmedEvent(
    UUID id,
    UUID completionId,
    UUID enrollmentId,
    UUID courseId,
    Instant occurredAt
) {
    public CompletionConfirmedEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(completionId, "completionId");
        Objects.requireNonNull(enrollmentId, "enrollmentId");
        Objects.requireNonNull(courseId, "courseId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
