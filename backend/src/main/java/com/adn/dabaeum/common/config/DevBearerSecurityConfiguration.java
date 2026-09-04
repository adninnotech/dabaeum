package com.adn.dabaeum.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
@EnableConfigurationProperties(DevBearerTokenProperties.class)
public class DevBearerSecurityConfiguration {

    @Bean
    DevBearerAuthenticationFilter devBearerAuthenticationFilter(
        DevBearerTokenProperties properties
    ) {
        return new DevBearerAuthenticationFilter(properties);
    }

    @Bean
    SecurityErrorWriter securityErrorWriter(ObjectMapper objectMapper, Clock clock) {
        return new SecurityErrorWriter(objectMapper, clock);
    }

    @Bean
    UserDetailsService devBearerUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("User is not available");
        };
    }
}
