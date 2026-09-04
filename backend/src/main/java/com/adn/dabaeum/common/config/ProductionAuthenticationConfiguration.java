package com.adn.dabaeum.common.config;

import com.adn.dabaeum.authentication.application.AuthenticatedUserContextFactory;
import com.adn.dabaeum.authentication.application.AuthenticationPort;
import com.adn.dabaeum.authentication.application.JwtAuthenticationAdapter;
import com.adn.dabaeum.authentication.application.JwtAuthenticationProperties;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Profile("production")
@EnableConfigurationProperties(JwtAuthenticationProperties.class)
public class ProductionAuthenticationConfiguration {

    @Bean
    AuthenticationPort authenticationPort(
        JwtAuthenticationProperties properties,
        AuthenticatedUserContextFactory contextFactory,
        UserRepository userRepository,
        Clock clock,
        ObjectMapper objectMapper
    ) {
        return new JwtAuthenticationAdapter(
            properties,
            contextFactory,
            userRepository,
            clock,
            objectMapper
        );
    }

    @Bean
    SecurityErrorWriter productionSecurityErrorWriter(
        ObjectMapper objectMapper,
        Clock clock
    ) {
        return new SecurityErrorWriter(objectMapper, clock);
    }

    @Bean
    ProductionJwtAuthenticationFilter productionJwtAuthenticationFilter(
        AuthenticationPort authenticationPort,
        SecurityErrorWriter securityErrorWriter
    ) {
        return new ProductionJwtAuthenticationFilter(
            authenticationPort,
            securityErrorWriter
        );
    }
}
