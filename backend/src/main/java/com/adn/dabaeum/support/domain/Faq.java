package com.adn.dabaeum.support.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Faq(
    UUID id,
    String question,
    String answer,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt
) {

    public Faq {
        Objects.requireNonNull(id, "id");
        if (question == null || question.isBlank() || question.length() > 500) {
            throw new IllegalArgumentException("question must be 1..500 characters");
        }
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("answer must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
