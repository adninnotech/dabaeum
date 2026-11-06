package com.adn.dabaeum.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RequestRateLimiterTest {

    @Test
    void rejectsTheNextRequestWithinWindowAndReturnsRetrySeconds() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC);
        RequestRateLimiter limiter = new RequestRateLimiter(clock);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.allow("reset:test@example.com", 3, Duration.ofHours(1)))
                .isZero();
        }
        assertThat(limiter.allow("reset:test@example.com", 3, Duration.ofHours(1)))
            .isEqualTo(3600);
        assertThat(limiter.allow("reset:other@example.com", 3, Duration.ofHours(1)))
            .isZero();
    }

}
