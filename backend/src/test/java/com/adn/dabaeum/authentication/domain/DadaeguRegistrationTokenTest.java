package com.adn.dabaeum.authentication.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DadaeguRegistrationTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void requiresHashDidAndName() {
        assertThatThrownBy(() -> token("short", "did:x:1", "홍길동"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> token("a".repeat(64), "not-a-did", "홍길동"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> token("a".repeat(64), "did:x:1", " "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static DadaeguRegistrationToken token(String hash, String did, String name) {
        return new DadaeguRegistrationToken(
            UUID.randomUUID(), hash, did, name, null, null, NOW.plusSeconds(600), null, NOW);
    }
}
