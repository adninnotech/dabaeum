package com.adn.dabaeum.authentication.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 다대구 첫 로그인에서 인증은 끝났지만 가입(구·군·약관)이 남은 상태 (설계서 §4.2).
 * 다대구가 준 신원 값을 서버가 들고 있다가 가입 요청에서 꺼낸다 — 프론트를 거치지 않는다.
 */
public record DadaeguRegistrationToken(
    UUID id,
    String tokenHash,
    String did,
    String name,
    String phone,
    LocalDate birthDate,
    Instant expiresAt,
    Instant usedAt,
    Instant createdAt
) {

    public DadaeguRegistrationToken {
        Objects.requireNonNull(id, "id");
        if (tokenHash == null || tokenHash.length() != 64) {
            throw new IllegalArgumentException("tokenHash must be a 64-character hash");
        }
        if (did == null || !did.startsWith("did:")) {
            throw new IllegalArgumentException("did is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
