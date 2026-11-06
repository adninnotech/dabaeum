package com.adn.dabaeum.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adn.dabaeum.common.web.RequestIdFilter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** CORS 는 허용 목록이 설정된 경우에만 열린다. 기본(빈 목록)은 교차 출처 사전 요청을 거부한다. */
class CorsConfigurationTest {

    @WebMvcTest(SecurityConfigurationTest.ProbeController.class)
    @ActiveProfiles("local")
    @TestPropertySource(properties = {
        "dabaeum.security.dev.bearer-token=unit-test-token",
        // local yml 은 개발 편의로 localhost 를 열어 둔다. 이 테스트는 "빈 목록이면 닫힌다"를 yml 과 무관하게 검증한다.
        "dabaeum.security.cors.allowed-origins="
    })
    @Import({SecurityConfiguration.class, ClockConfiguration.class, DevBearerSecurityConfiguration.class, LocalDevSecuritySupportConfiguration.class,
        RequestIdFilter.class, SecurityConfigurationTest.ProbeController.class})
    @Nested
    class Disabled {

        @Autowired
        MockMvc mockMvc;

        @Test
        void noCorsHeaderIsWrittenForCrossOriginRequests() throws Exception {
            // 허용 목록이 없으면 CORS 필터가 아무것도 쓰지 않아 브라우저가 응답을 차단한다.
            mockMvc.perform(options("/api/v1/system/ping")
                    .header("Origin", "http://localhost:8000")
                    .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
            mockMvc.perform(get("/api/v1/system/ping").header("Origin", "http://localhost:8000"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @WebMvcTest(SecurityConfigurationTest.ProbeController.class)
    @ActiveProfiles("local")
    @TestPropertySource(properties = {
        "dabaeum.security.dev.bearer-token=unit-test-token",
        "dabaeum.security.cors.allowed-origins=http://localhost:8000, null"
    })
    @Import({SecurityConfiguration.class, ClockConfiguration.class, DevBearerSecurityConfiguration.class, LocalDevSecuritySupportConfiguration.class,
        RequestIdFilter.class, SecurityConfigurationTest.ProbeController.class})
    @Nested
    class Enabled {

        @Autowired
        MockMvc mockMvc;

        @Test
        void preflightAndActualRequestCarryCorsHeadersForListedOrigins() throws Exception {
            mockMvc.perform(options("/api/v1/institutions")
                    .header("Origin", "http://localhost:8000")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8000"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
            // 파일로 연 페이지(file://)는 Origin 이 "null" 이다.
            mockMvc.perform(get("/api/v1/system/ping").header("Origin", "null"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "null"));
        }

        @Test
        void unlistedOriginIsStillRejected() throws Exception {
            mockMvc.perform(options("/api/v1/system/ping")
                    .header("Origin", "http://evil.example")
                    .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        }
    }
}
