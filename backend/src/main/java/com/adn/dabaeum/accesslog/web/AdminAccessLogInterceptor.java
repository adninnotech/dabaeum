package com.adn.dabaeum.accesslog.web;

import com.adn.dabaeum.accesslog.domain.AdminAccessLogRepository;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** 응답이 끝난 뒤 접속기록 한 줄을 쓴다. 실패해도 응답에는 영향이 없다 — 이미 나갔다. */
public final class AdminAccessLogInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAccessLogInterceptor.class);

    private final ObjectProvider<AdminAccessLogRepository> repository;
    private final Clock clock;
    private final CurrentUserProvider currentUser = new CurrentUserProvider();

    public AdminAccessLogInterceptor(ObjectProvider<AdminAccessLogRepository> repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void afterCompletion(
        HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception
    ) {
        if (!(handler instanceof HandlerMethod method)) {
            return;
        }
        PersonalDataAccess access = method.getMethodAnnotation(PersonalDataAccess.class);
        AdminAccessLogRepository logs = repository.getIfAvailable();
        if (access == null || logs == null) {
            return;
        }
        String endpoint = request.getMethod() + " "
            + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        try {
            AdminAccessLogFactory.create(new AdminAccessLogFactory.AccessEvent(
                    UUID.randomUUID(), actor().orElse(null), access.action(), endpoint, pathUserId(request),
                    (CapturedTargets) request.getAttribute(AccessLogTargetsAdvice.ATTRIBUTE),
                    response.getStatus(), request.getRemoteAddr(), request.getHeader("User-Agent"),
                    requestId(request), clock.instant()))
                .ifPresent(logs::save);
        } catch (RuntimeException failure) {
            LOGGER.error("ADMIN_ACCESS_LOG_WRITE_FAILED endpoint={} type={}",
                endpoint, failure.getClass().getName(), failure);
        }
    }

    private Optional<AuthenticatedUserContext> actor() {
        try {
            return currentUser.findContext();
        } catch (ApiException notAUserPrincipal) {
            // dev 고정 토큰(문자열 principal)은 사용자 문맥이 없다. 기록 대상이 아니다.
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static UUID pathUserId(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> map && map.get("userId") instanceof String value) {
            return parseUuid(value);
        }
        return null;
    }

    private static UUID requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME) instanceof String value ? parseUuid(value) : null;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
