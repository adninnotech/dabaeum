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
    void prettyJsonModeWritesSummaryLineThenIndentedBodiesStillMasked() throws Exception {
        List<String> messages = new ArrayList<>();
        HttpTrafficLoggingFilter filter = new HttpTrafficLoggingFilter(
            new ObjectMapper(),
            messages::add,
            new HttpTrafficLoggingProperties(true, true, true)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
        request.setContentType("application/json");
        request.setContent("{\"name\":\"tester\",\"password\":\"body-secret\"}".getBytes());
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            ((HttpServletRequest) servletRequest).getInputStream().readAllBytes();
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"data\":{\"status\":\"ok\"}}");
        });

        String message = messages.getFirst();
        assertThat(message.lines().findFirst().orElseThrow())
            .startsWith("http_exchange requestId=request-123 method=POST path=/api/v1/test")
            .doesNotContain("requestBody");
        assertThat(message)
            .contains("\nrequestBody:\n{\n  \"name\" : \"tester\",")
            .contains("\"password\" : \"***MASKED***\"")
            .contains("\nresponseBody:\n{\n  \"data\" : {\n    \"status\" : \"ok\"")
            .doesNotContain("body-secret");
    }

    @Test
    void compactModeKeepsOneLine() throws Exception {
        List<String> messages = new ArrayList<>();
        HttpTrafficLoggingFilter filter = new HttpTrafficLoggingFilter(
            new ObjectMapper(),
            messages::add,
            new HttpTrafficLoggingProperties(true, true, false)
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            servletResponse.setContentType("application/json");
            servletResponse.getWriter().write("{\"data\":{\"status\":\"ok\"}}");
        });

        assertThat(messages.getFirst()).doesNotContain("\n")
            .contains("responseBody={\"data\":{\"status\":\"ok\"}}");
    }

    @Test
    void devLogOmitsCredentialsAndPersonalQueryValuesButKeepsSummary() throws Exception {
        List<String> messages = new ArrayList<>();
        HttpTrafficLoggingFilter filter = new HttpTrafficLoggingFilter(
            new ObjectMapper(), messages::add,
            new HttpTrafficLoggingProperties(false, true, false));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setQueryString("email=person@example.com");
        request.setContentType("application/json");
        request.setContent("{\"password\":\"login-secret\"}".getBytes());
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            ((HttpServletRequest) servletRequest).getInputStream().readAllBytes();
            servletResponse.setContentType("application/json");
            ((HttpServletResponse) servletResponse).setStatus(200);
            servletResponse.getWriter().write("{\"accessToken\":\"jwt-secret\"}");
        });

        assertThat(messages.getFirst())
            .contains("requestId=request-123 method=POST path=/api/v1/auth/login")
            .contains("status=200 durationMs=")
            .contains("query=<omitted>")
            .contains("requestBody=<body-logging-off> responseBody=<body-logging-off>")
            .doesNotContain("person@example.com", "login-secret", "jwt-secret");
    }

    @Test
    void publicPortfolioTokenIsMaskedInPath() throws Exception {
        List<String> messages = new ArrayList<>();
        HttpTrafficLoggingFilter filter = new HttpTrafficLoggingFilter(
            new ObjectMapper(), messages::add,
            new HttpTrafficLoggingProperties(false, true, false));
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET", "/api/v1/public/portfolios/share-secret");

        filter.doFilter(request, new MockHttpServletResponse(),
            (servletRequest, servletResponse) -> {});

        assertThat(messages.getFirst())
            .contains("path=/api/v1/public/portfolios/***MASKED***")
            .doesNotContain("share-secret");
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
