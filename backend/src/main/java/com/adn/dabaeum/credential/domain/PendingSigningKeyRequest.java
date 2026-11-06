package com.adn.dabaeum.credential.domain;

import java.util.Objects;
import java.util.UUID;

/** 키 생성 워커가 가져간 기관 서명키 생성 요청. retryCount 는 지금까지 미룬 횟수다. */
public record PendingSigningKeyRequest(
    UUID eventId,
    UUID institutionId,
    UUID requestedBy,
    int retryCount
) {

    public PendingSigningKeyRequest {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(institutionId, "institutionId");
        Objects.requireNonNull(requestedBy, "requestedBy");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
    }
}
