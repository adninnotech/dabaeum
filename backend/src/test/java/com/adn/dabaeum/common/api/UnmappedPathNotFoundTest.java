package com.adn.dabaeum.common.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.config.ClockConfiguration;
import com.adn.dabaeum.common.config.DevBearerSecurityConfiguration;
import com.adn.dabaeum.common.config.LocalDevSecuritySupportConfiguration;
import com.adn.dabaeum.common.config.JacksonConfiguration;
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

/**
 * 매핑 없는 경로는 404 여야 한다.
 *
 * <p>{@code ApiExceptionHandler} 의 포괄 핸들러({@code Exception.class})가 스프링의
 * {@code NoResourceFoundException} 까지 삼켜 500 이 나가던 버그의 회귀 테스트다. 500 이 나가면
 * 호출하는 쪽에서 "오타 난 경로" 와 "서버 장애" 를 구분할 수 없어 연동 디버깅이 막힌다.
 */
@SpringBootTest(classes = UnmappedPathNotFoundTest.TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties =
    "dabaeum.security.dev.bearer-token=unmapped-path-not-found-test")
class UnmappedPathNotFoundTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 매핑_없는_경로는_404_와_ENDPOINT_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/v1/there-is-no-such-thing").with(user("u").roles("LEARNER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
    }

    @Test
    void 매핑_없는_경로에_비로그인이면_401_과_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get("/api/v1/there-is-no-such-thing"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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
    }
}
