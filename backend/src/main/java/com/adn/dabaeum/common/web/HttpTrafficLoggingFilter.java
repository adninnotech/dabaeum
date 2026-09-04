package com.adn.dabaeum.common.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Order(Ordered.LOWEST_PRECEDENCE)
public final class HttpTrafficLoggingFilter extends OncePerRequestFilter {

    static final String MASKED_VALUE = "***MASKED***";
    static final int MAX_BODY_CHARS = 4_096;

    private static final Logger LOGGER = LoggerFactory.getLogger(
        HttpTrafficLoggingFilter.class
    );

    private final ObjectMapper objectMapper;
    private final Consumer<String> logSink;
    private final HttpTrafficLoggingProperties properties;

    public HttpTrafficLoggingFilter(ObjectMapper objectMapper) {
        this(objectMapper, new HttpTrafficLoggingProperties(true, true));
    }

    public HttpTrafficLoggingFilter(
        ObjectMapper objectMapper,
        HttpTrafficLoggingProperties properties
    ) {
        this(objectMapper, message -> LOGGER.info("{}", message), properties);
    }

    HttpTrafficLoggingFilter(
        ObjectMapper objectMapper,
        Consumer<String> logSink
    ) {
        this(objectMapper, logSink, new HttpTrafficLoggingProperties(true, true));
    }

    HttpTrafficLoggingFilter(
        ObjectMapper objectMapper,
        Consumer<String> logSink,
        HttpTrafficLoggingProperties properties
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.logSink = Objects.requireNonNull(logSink);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        ContentCachingRequestWrapper cachedRequest = request
            instanceof ContentCachingRequestWrapper existing
            ? existing
            : new ContentCachingRequestWrapper(request, MAX_BODY_CHARS);
        ContentCachingResponseWrapper cachedResponse = response
            instanceof ContentCachingResponseWrapper existing
            ? existing
            : new ContentCachingResponseWrapper(response);
        long startedAt = System.nanoTime();

        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            try {
                logExchange(cachedRequest, cachedResponse, startedAt);
            } finally {
                cachedResponse.copyBodyToResponse();
            }
        }
    }

    private void logExchange(
        ContentCachingRequestWrapper request,
        ContentCachingResponseWrapper response,
        long startedAt
    ) {
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        String requestId = requestId(request, response);
        String message = "http_exchange"
            + " requestId=" + requestId
            + " method=" + request.getMethod()
            + " path=" + request.getRequestURI()
            + " query=" + sanitizeQueryForLogging(request.getQueryString())
            + " status=" + response.getStatus()
            + " durationMs=" + durationMs
            + " requestBody=" + bodyForLogging(
                request.getContentAsByteArray(),
                request.getContentType()
            )
            + " responseBody=" + bodyForLogging(
                response.getContentAsByteArray(),
                response.getContentType()
            );
        logSink.accept(message);
    }

    private String requestId(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        Object attribute = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        String header = response.getHeader(RequestIdFilter.HEADER_NAME);
        return header == null || header.isBlank() ? "<none>" : header;
    }

    private String bodyForLogging(byte[] body, String contentType) {
        if (!properties.bodyEnabled()) {
            return "<body-logging-off>";
        }
        if (body.length == 0) {
            return "<empty>";
        }
        if (!isJsonContentType(contentType)) {
            return "<omitted contentType=" + String.valueOf(contentType) + ">";
        }
        return sanitizeJsonForLogging(
            new String(body, StandardCharsets.UTF_8)
        );
    }

    String sanitizeJsonForLogging(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return "<empty>";
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null) {
                return "<empty>";
            }
            maskNode(root);
            return truncate(objectMapper.writeValueAsString(root));
        } catch (RuntimeException exception) {
            return "<omitted invalid-json>";
        }
    }

    String sanitizeQueryForLogging(String query) {
        if (query == null || query.isBlank()) {
            return "<none>";
        }
        StringBuilder sanitized = new StringBuilder(query.length());
        String[] pairs = query.split("&", -1);
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                sanitized.append('&');
            }
            String pair = pairs[i];
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            if (isSensitiveKey(key)) {
                sanitized.append(key).append('=').append(MASKED_VALUE);
            } else {
                sanitized.append(pair);
            }
        }
        return truncate(sanitized.toString());
    }

    private void maskNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.properties()
                .iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveKey(field.getKey())) {
                    object.put(field.getKey(), MASKED_VALUE);
                } else {
                    maskNode(field.getValue());
                }
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                maskNode(child);
            }
        }
    }

    private boolean isJsonContentType(String contentType) {
        return contentType != null
            && contentType.toLowerCase(Locale.ROOT).contains("json");
    }

    private boolean isSensitiveKey(String key) {
        if (!properties.maskSensitive()) {
            return false;
        }
        String normalized = key == null
            ? ""
            : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.contains("authorization")
            || normalized.contains("password")
            || normalized.contains("passphrase")
            || normalized.contains("token")
            || normalized.contains("secret")
            || normalized.contains("privatekey")
            || normalized.contains("certificate")
            || normalized.contains("apikey")
            || normalized.endsWith("key")
            || normalized.equals("jwt")
            || normalized.equals("privsk")
            || normalized.equals("key")
            || normalized.equals("requesterid")
            || (normalized.contains("credential")
                && !normalized.endsWith("id"));
    }

    private String truncate(String value) {
        if (value.length() <= MAX_BODY_CHARS) {
            return value;
        }
        return value.substring(0, MAX_BODY_CHARS) + "…(truncated)";
    }
}
