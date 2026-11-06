package com.adn.dabaeum.common.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.SecurityConfiguration;
import com.adn.dabaeum.common.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경로는 있지만 메서드가 다르면 405 와 Allow 헤더여야 한다.
 *
 * <p>{@code ApiExceptionHandler} 의 포괄 핸들러가 {@code HttpRequestMethodNotSupportedException} 까지
 * 삼켜 500 이 나가던 버그(QA 이슈 #50, POST 전용 경로에 GET)의 회귀 테스트다.
 */
@SpringBootTest(classes = MethodNotAllowedTest.TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=method-not-allowed-test")
class MethodNotAllowedTest {

    private static final String POST_ONLY_PATH = "/api/v1/completions/{completionId}/credentials";

    @Autowired
    MockMvc mockMvc;

    @Test
    void POST_전용_경로에_GET_은_405_와_METHOD_NOT_ALLOWED_와_Allow_헤더() throws Exception {
        mockMvc.perform(get(POST_ONLY_PATH, "00000000-0000-0000-0000-000000000000")
                .with(user("u").roles("LEARNER")))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", "POST"))
            .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
            .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void POST_전용_경로에_비로그인_GET_은_401_과_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get(POST_ONLY_PATH, "00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @RestController
    static class PostOnlyController {

        @PostMapping(POST_ONLY_PATH)
        void issue(@PathVariable String completionId) {
        }
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
        RequestIdFilter.class,
        PostOnlyController.class
    })
    static class TestApplication {
    }
}
