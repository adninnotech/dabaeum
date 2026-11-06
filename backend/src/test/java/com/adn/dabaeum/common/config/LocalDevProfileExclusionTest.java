package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

/**
 * local 과 dev 가 함께 켜지면 인터넷에 열린 dev 서버에서 고정 Bearer 토큰(local 전용)이 살아나고,
 * JWT kid 도 프로필 순서에 따라 local 값이 이긴다. 둘을 함께 켜는 것 자체를 막는다.
 */
class LocalDevProfileExclusionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(LocalDevSecuritySupportConfiguration.class)
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void local_과_dev_를_함께_켜면_기동을_거부한다() {
        runner.withPropertyValues("spring.profiles.active=dev,local")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("'local' and 'dev' profiles must not be active together");
            });
    }

    @Test
    void 순서를_바꿔도_거부한다() {
        runner.withPropertyValues("spring.profiles.active=local,dev")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void dev_만_켜면_기동한다() {
        runner.withPropertyValues("spring.profiles.active=dev")
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void local_만_켜면_기동한다() {
        runner.withPropertyValues("spring.profiles.active=local")
            .run(context -> assertThat(context).hasNotFailed());
    }
}
