package com.adn.dabaeum.common.config;

import com.adn.dabaeum.authentication.application.LocalJwtProperties;
import com.adn.dabaeum.authentication.application.LocalJwtTokenService;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
@EnableConfigurationProperties(LocalJwtProperties.class)
public class LocalAccountSecurityConfiguration {

    @Bean
    PasswordEncoder localAccountPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    LocalJwtTokenService localJwtTokenService(
        LocalJwtProperties properties,
        UserRepository userRepository,
        UserRoleRepository userRoleRepository,
        Clock clock,
        ObjectMapper objectMapper
    ) {
        return new LocalJwtTokenService(
            properties,
            userRepository,
            userRoleRepository,
            clock,
            objectMapper
        );
    }

    @Bean
    LocalAccountAuthenticationFilter localAccountAuthenticationFilter(
        LocalJwtTokenService tokenService,
        SecurityErrorWriter securityErrorWriter
    ) {
        return new LocalAccountAuthenticationFilter(
            tokenService,
            securityErrorWriter
        );
    }
}
