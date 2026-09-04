package com.adn.dabaeum.authentication.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.adn.dabaeum.authentication.application.AuthenticatedUserContextFactory;
import com.adn.dabaeum.authentication.application.AuthenticationPort;
import com.adn.dabaeum.authentication.application.JwtAuthenticationAdapter;
import com.adn.dabaeum.common.config.ProductionAuthenticationConfiguration;
import com.adn.dabaeum.common.config.ProductionJwtAuthenticationFilter;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class ProductionAuthenticationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(ProductionAuthenticationConfiguration.class)
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(AuthenticatedUserContextFactory.class, () -> mock(
            AuthenticatedUserContextFactory.class
        ))
        .withBean(UserRepository.class, () -> mock(UserRepository.class))
        .withPropertyValues(
            "dabaeum.security.production.jwt.issuer=https://issuer.example.test",
            "dabaeum.security.production.jwt.audience=dabaeum-web",
            "dabaeum.security.production.jwt.signing-keys.key-1="
                + Base64.getEncoder().encodeToString(
                    "test-key-material".getBytes()
                )
        );

    @Test
    void createsProductionAuthenticationOnlyForProductionProfile() {
        runner.withPropertyValues("spring.profiles.active=production")
            .run(context -> {
                assertThat(context.getBean(AuthenticationPort.class))
                    .isInstanceOf(JwtAuthenticationAdapter.class);
                assertThat(context.getBean(ProductionJwtAuthenticationFilter.class))
                    .isNotNull();
            });

        runner.withPropertyValues("spring.profiles.active=test")
            .run(context -> {
                assertThat(context.getBeansOfType(AuthenticationPort.class))
                    .isEmpty();
                assertThat(context.getBeansOfType(
                    ProductionJwtAuthenticationFilter.class
                )).isEmpty();
            });
    }
}
