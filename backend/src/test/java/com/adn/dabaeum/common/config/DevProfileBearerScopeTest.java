package com.adn.dabaeum.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.authentication.application.AuthenticationPort;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiExceptionHandler;
import com.adn.dabaeum.common.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 고정 Bearer 토큰은 로그인 없이 PLATFORM_ADMIN 을 준다. 인터넷에 열린 dev 서버에서는 켜지면 안 된다.
 * dev 에서는 같은 토큰이 로그인 JWT 필터로 넘어가 401 이 나야 하고, Bearer 필터가 빠져도
 * 로그인 JWT 필터 순서와 공용 보안 빈이 그대로 살아 있어야 한다.
 */
@SpringBootTest(classes = DevProfileBearerScopeTest.TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=dev-scope-test-token-0001")
class DevProfileBearerScopeTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApplicationContext context;

    @Test
    void dev_에서는_고정_Bearer_토큰이_관리자로_통과하지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/there-is-no-such-thing")
                .header("Authorization", "Bearer dev-scope-test-token-0001"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void dev_에는_Bearer_필터가_없고_공용_보안_빈은_남는다() {
        assertThat(context.getBeanNamesForType(DevBearerAuthenticationFilter.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DevBearerTokenProperties.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SecurityErrorWriter.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).hasSize(1);
    }

    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        MybatisAutoConfiguration.class
    })
    @Import({
        SecurityConfiguration.class,
        DevBearerSecurityConfiguration.class,
        LocalDevSecuritySupportConfiguration.class,
        ApiExceptionHandler.class,
        ClockConfiguration.class,
        JacksonConfiguration.class,
        RequestIdFilter.class
    })
    static class TestApplication {

        @Bean
        LocalAccountAuthenticationFilter localAccountAuthenticationFilter(
            SecurityErrorWriter securityErrorWriter
        ) {
            AuthenticationPort rejectAll = token -> {
                throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ApiErrorCode.AUTHENTICATION_FAILED,
                    "Authentication failed"
                );
            };
            return new LocalAccountAuthenticationFilter(rejectAll, securityErrorWriter);
        }
    }
}
