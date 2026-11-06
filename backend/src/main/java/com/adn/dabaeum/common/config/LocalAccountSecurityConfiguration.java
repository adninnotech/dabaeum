package com.adn.dabaeum.common.config;

import com.adn.dabaeum.authentication.application.LocalJwtProperties;
import com.adn.dabaeum.authentication.application.LocalJwtTokenService;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import java.util.Locale;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
@EnableConfigurationProperties(LocalJwtProperties.class)
public class LocalAccountSecurityConfiguration {

    private static final String DEV_PROFILE = "dev";
    private static final String LOCAL_KEY_ID_PREFIX = "local-";

    /**
     * local JWT 키는 개발자 PC 마다 있다. dev 가 local 키로 뜨면 그 PC 를 가진 사람이 dev 토큰을 위조할 수
     * 있다. 키 값은 비밀이라 비교하지 않고 kid 로 판단한다. 대소문자는 가리지 않는다.
     */
    @Bean
    InitializingBean localJwtKeyIdGuard(Environment environment, LocalJwtProperties properties) {
        return () -> {
            if (environment.acceptsProfiles(Profiles.of(DEV_PROFILE))
                && properties.keyId().toLowerCase(Locale.ROOT).startsWith(LOCAL_KEY_ID_PREFIX)) {
                throw new IllegalStateException(
                    "local-account JWT key-id '" + properties.keyId()
                        + "' is not allowed in the '" + DEV_PROFILE + "' profile");
            }
        };
    }

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
