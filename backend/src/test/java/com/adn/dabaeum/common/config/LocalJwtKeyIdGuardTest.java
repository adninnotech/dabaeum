package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

/**
 * local JWT 키는 저장소에 있다. dev 가 local 키로 뜨면 저장소를 본 사람이 dev 토큰을 위조할 수 있으므로,
 * 비밀이 아닌 kid 로 판단해 기동을 막는다.
 */
class LocalJwtKeyIdGuardTest {

    // 테스트 전용 값. 32바이트 0 을 Base64 로 쓴 것이다.
    private static final String TEST_SIGNING_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(
            LocalAccountSecurityConfiguration.class,
            LocalDevSecuritySupportConfiguration.class)
        .withBean(UserRepository.class, () -> mock(UserRepository.class))
        .withBean(UserRoleRepository.class, () -> mock(UserRoleRepository.class))
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withPropertyValues(
            "dabaeum.security.local-account.jwt.issuer=dabaeum-local",
            "dabaeum.security.local-account.jwt.audience=dabaeum-api",
            "dabaeum.security.local-account.jwt.signing-key=" + TEST_SIGNING_KEY,
            "dabaeum.security.local-account.jwt.access-token-ttl=1h");

    @Test
    void dev_프로필에서_local_kid_면_기동을_거부한다() {
        runner.withPropertyValues(
                "spring.profiles.active=dev",
                "dabaeum.security.local-account.jwt.key-id=local-1")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("key-id 'local-1' is not allowed in the 'dev' profile");
            });
    }

    @Test
    void dev_프로필에서_대문자_local_kid_도_거부한다() {
        runner.withPropertyValues(
                "spring.profiles.active=dev",
                "dabaeum.security.local-account.jwt.key-id=LOCAL-1")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void dev_프로필에서_dev_kid_는_허용한다() {
        runner.withPropertyValues(
                "spring.profiles.active=dev",
                "dabaeum.security.local-account.jwt.key-id=dev-1")
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void local_프로필에서는_local_kid_를_허용한다() {
        runner.withPropertyValues(
                "spring.profiles.active=local",
                "dabaeum.security.local-account.jwt.key-id=local-1")
            .run(context -> assertThat(context).hasNotFailed());
    }
}
