package com.adn.dabaeum.authentication.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.adn.dabaeum.authentication.application.LocalJwtTokenService;
import com.adn.dabaeum.common.config.LocalAccountAuthenticationFilter;
import com.adn.dabaeum.common.config.LocalAccountSecurityConfiguration;
import com.adn.dabaeum.common.config.SecurityErrorWriter;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

class LocalAccountSecurityConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(LocalAccountSecurityConfiguration.class)
        .withBean(UserRepository.class, () -> mock(UserRepository.class))
        .withBean(UserRoleRepository.class, () -> mock(UserRoleRepository.class))
        .withBean(SecurityErrorWriter.class, () -> mock(SecurityErrorWriter.class))
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withPropertyValues(
            "dabaeum.security.local-account.jwt.issuer=dabaeum-local",
            "dabaeum.security.local-account.jwt.audience=dabaeum-api",
            "dabaeum.security.local-account.jwt.key-id=local-development-1",
            "dabaeum.security.local-account.jwt.signing-key=" + Base64.getEncoder()
                .encodeToString("local-account-test-signing-key-32".getBytes()),
            "dabaeum.security.local-account.jwt.access-token-ttl=1h"
        );

    @Test
    void createsLocalAccountSecurityOnlyForLocalAndDevProfiles() {
        runner.withPropertyValues("spring.profiles.active=test")
            .run(context -> {
                assertThat(context.getBeansOfType(LocalJwtTokenService.class)).isEmpty();
                assertThat(context.getBeansOfType(
                    LocalAccountAuthenticationFilter.class
                )).isEmpty();
            });

        runner.withPropertyValues("spring.profiles.active=local")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(LocalJwtTokenService.class)).isNotNull();
                assertThat(context.getBean(LocalAccountAuthenticationFilter.class))
                    .isNotNull();
                assertThat(context.getBean(PasswordEncoder.class))
                    .isInstanceOf(BCryptPasswordEncoder.class);
            });
    }
}
