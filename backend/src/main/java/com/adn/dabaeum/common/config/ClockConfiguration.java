package com.adn.dabaeum.common.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    /**
     * 마이크로초 단위로 끊는다. PostgreSQL TIMESTAMPTZ 는 마이크로초까지만 저장하므로, 나노초
     * 정밀도(Windows JDK 는 100ns) 시각을 그대로 쓰면 메모리의 값과 DB 에서 읽은 값이 달라
     * 동일성 비교가 깨진다. 저장 가능한 정밀도로 맞춰 두면 그런 불일치가 생기지 않는다.
     */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
