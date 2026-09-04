package com.adn.dabaeum.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class HttpTrafficLoggingFilterTest {

    @Test
    void masksSensitiveJsonFieldsAndQueryValues() {
        List<String> messages = new ArrayList<>();
        HttpTrafficLoggingFilter filter = new HttpTrafficLoggingFilter(
            new ObjectMapper(),
            messages::add
        );

        String sanitized = filter.sanitizeJsonForLogging(
            "{\"name\":\"tester\",\"password\":\"pw\","
                + "\"apiKey\":\"key\",\"certificate\":\"cert\","
                + "\"passwordHash\":\"bcrypt-hash\","
                + "\"requesterId\":\"person@example.com\","
                + "\"nested\":{\"accessToken\":\"token\"}}"
        );

        assertThat(sanitized)
            .contains("\"name\":\"tester\"")
            .contains("\"password\":\"***MASKED***\"")
            .contains("\"passwordHash\":\"***MASKED***\"")
            .contains("\"apiKey\":\"***MASKED***\"")
            .contains("\"certificate\":\"***MASKED***\"")
            .contains("\"requesterId\":\"***MASKED***\"")
            .contains("\"accessToken\":\"***MASKED***\"")
            .doesNotContain("pw")
            .doesNotContain("bcrypt-hash")
            .doesNotContain("person@example.com")
            .doesNotContain("token");
        assertThat(filter.sanitizeQueryForLogging(
            "page=0&token=secret-token&sort=createdAt,desc"
        )).isEqualTo("page=0&token=***MASKED***&sort=createdAt,desc");
    }

    @Test
    void logsJsonExchangeWithoutLeakingSensitiveValuesAndPreservesResponse() throws Exception {
        List<String> messages = new ArrayList<>();
        HttpTrafficLoggingFilter filter = new HttpTrafficLoggingFilter(
            new ObjectMapper(),
            messages::add
        );
        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST",
            "/api/v1/test"
        );
        request.setQueryString("page=0&token=query-secret");
        request.setContentType("application/json");
        request.setContent(
            "{\"name\":\"tester\",\"password\":\"body-secret\"}"
                .getBytes()
        );
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (servletRequest, servletResponse) -> {
            ((HttpServletRequest) servletRequest).getInputStream().readAllBytes();
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setContentType("application/json");
            httpResponse.setStatus(201);
            httpResponse.getWriter().write(
                "{\"accessToken\":\"response-secret\",\"status\":\"ok\"}"
            );
        };

        filter.doFilter(
            request,
            response,
            chain
        );

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString())
            .contains("\"status\":\"ok\"")
            .contains("response-secret");
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst())
            .contains("requestId=request-123")
            .contains("method=POST")
            .contains("status=201")
            .contains("token=***MASKED***")
            .contains("password\":\"***MASKED***")
            .doesNotContain("body-secret")
            .doesNotContain("query-secret")
            .doesNotContain("response-secret");
    }

    @Test
    void hasLocalAndDevProfileOnly() {
        org.springframework.context.annotation.Profile profile =
            HttpTrafficLoggingConfiguration.class.getAnnotation(
                org.springframework.context.annotation.Profile.class
            );

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactlyInAnyOrder("local", "dev");
    }
}
