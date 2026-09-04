package com.adn.dabaeum.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = "requestId";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = validUuidOrNew(request.getHeader(HEADER_NAME));

        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId);

        filterChain.doFilter(request, response);
    }

    private String validUuidOrNew(String candidate) {
        if (candidate != null) {
            try {
                return UUID.fromString(candidate).toString();
            } catch (IllegalArgumentException ignored) {
                // 형식이 잘못된 입력에는 새 요청 ID를 발급한다.
            }
        }
        return UUID.randomUUID().toString();
    }
}
