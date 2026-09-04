package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * 애플리케이션 시각은 PostgreSQL TIMESTAMPTZ 가 저장할 수 있는 마이크로초 단위여야 한다.
 * JDK 의 시스템 시계 정밀도는 OS 마다 다르므로(Linux·macOS 는 µs, Windows 는 100ns),
 * 여기서 잘라 두지 않으면 메모리 값과 DB 조회값이 달라 동일성 비교가 OS 에 따라 깨진다.
 */
class ClockConfigurationTest {

    @Test
    void applicationClockNeverCarriesSubMicrosecondPrecision() {
        Clock clock = new ClockConfiguration().clock();

        for (int i = 0; i < 1_000; i++) {
            Instant now = clock.instant();
            assertThat(now.truncatedTo(ChronoUnit.MICROS)).isEqualTo(now);
        }
    }

    @Test
    void applicationClockStaysCloseToSystemTime() {
        Clock clock = new ClockConfiguration().clock();

        Instant system = Instant.now();
        Instant application = clock.instant();

        assertThat(application).isBetween(system.minusSeconds(1), system.plusSeconds(1));
    }
}
