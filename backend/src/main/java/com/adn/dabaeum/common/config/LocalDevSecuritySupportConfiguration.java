package com.adn.dabaeum.common.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;

/**
 * local 과 dev 가 함께 쓰는 보안 빈. 고정 Bearer 필터는 local 전용이라 여기에 두지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
public class LocalDevSecuritySupportConfiguration {

    /**
     * local 과 dev 가 함께 켜지면 인터넷에 열린 dev 서버에서 고정 Bearer 토큰(local 전용)이 살아나고,
     * JWT kid 도 프로필 순서에 따라 local 값이 이긴다. 둘을 함께 켜는 것 자체를 막는다.
     */
    @Bean
    InitializingBean localDevProfileExclusionGuard(Environment environment) {
        return () -> {
            if (environment.acceptsProfiles(Profiles.of("local"))
                && environment.acceptsProfiles(Profiles.of("dev"))) {
                throw new IllegalStateException(
                    "'local' and 'dev' profiles must not be active together");
            }
        };
    }

    @Bean
    SecurityErrorWriter securityErrorWriter(ObjectMapper objectMapper, Clock clock) {
        return new SecurityErrorWriter(objectMapper, clock);
    }

    // Spring 이 임시 비밀번호를 가진 기본 사용자를 만들지 않게 막는다.
    @Bean
    UserDetailsService localDevUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("User is not available");
        };
    }
}
