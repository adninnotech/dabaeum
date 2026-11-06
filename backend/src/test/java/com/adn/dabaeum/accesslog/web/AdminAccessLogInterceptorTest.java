package com.adn.dabaeum.accesslog.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.accesslog.domain.AccessLogAction;
import com.adn.dabaeum.accesslog.domain.AdminAccessLog;
import com.adn.dabaeum.accesslog.domain.AdminAccessLogRepository;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import com.adn.dabaeum.common.web.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

class AdminAccessLogInterceptorTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    static class SampleController {
        @PersonalDataAccess(action = AccessLogAction.READ, targetField = "id")
        public void annotated() {
        }

        public void plain() {
        }
    }

    private final List<AdminAccessLog> saved = new ArrayList<>();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void writesOneRowForAnnotatedAdminRequest() throws Exception {
        authenticate("PLATFORM_ADMIN");
        MockHttpServletRequest request = request();

        interceptor(true).afterCompletion(request, response(200), handler("annotated"), null);

        assertThat(saved).singleElement().satisfies(log -> {
            assertThat(log.endpoint()).isEqualTo("GET /api/v1/users/{userId}");
            assertThat(log.targetUserIds()).containsExactly(TARGET);
            assertThat(log.ipAddress()).isEqualTo("203.0.113.7");
            assertThat(log.createdAt()).isEqualTo(NOW);
        });
    }

    @Test
    void ignoresHandlersWithoutAnnotation() throws Exception {
        authenticate("PLATFORM_ADMIN");

        interceptor(true).afterCompletion(request(), response(200), handler("plain"), null);

        assertThat(saved).isEmpty();
    }

    @Test
    void doesNothingWithoutRepositoryBean() throws Exception {
        authenticate("PLATFORM_ADMIN");

        interceptor(false).afterCompletion(request(), response(200), handler("annotated"), null);

        assertThat(saved).isEmpty();
    }

    @Test
    void repositoryFailureDoesNotPropagate() throws Exception {
        authenticate("PLATFORM_ADMIN");
        AdminAccessLogRepository failing = new RecordingRepository(log -> {
            throw new IllegalStateException("db down");
        });
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("repository", failing));
        AdminAccessLogInterceptor interceptor = new AdminAccessLogInterceptor(
            beans.getBeanProvider(AdminAccessLogRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));

        interceptor.afterCompletion(request(), response(200), handler("annotated"), null);
    }

    private AdminAccessLogInterceptor interceptor(boolean withRepository) {
        StaticListableBeanFactory beans = withRepository
            ? new StaticListableBeanFactory(Map.of("repository", new RecordingRepository(saved::add)))
            : new StaticListableBeanFactory();
        return new AdminAccessLogInterceptor(
            beans.getBeanProvider(AdminAccessLogRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new AuthenticatedUserPrincipal(ACTOR, Set.of(role)), "N/A",
            Set.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/" + TARGET);
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "junit");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/users/{userId}");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("userId", TARGET.toString()));
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, UUID.randomUUID().toString());
        return request;
    }

    private static MockHttpServletResponse response(int status) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        return response;
    }

    private static HandlerMethod handler(String name) throws NoSuchMethodException {
        return new HandlerMethod(new SampleController(), SampleController.class.getMethod(name));
    }

    private record RecordingRepository(java.util.function.Consumer<AdminAccessLog> sink)
        implements AdminAccessLogRepository {
        @Override
        public void save(AdminAccessLog log) {
            sink.accept(log);
        }

        @Override
        public int deleteCreatedBefore(Instant threshold) {
            return 0;
        }

        @Override
        public com.adn.dabaeum.accesslog.domain.AdminAccessLogPage search(
            com.adn.dabaeum.accesslog.domain.AdminAccessLogCriteria criteria
        ) {
            return new com.adn.dabaeum.accesslog.domain.AdminAccessLogPage(List.of(), 0);
        }
    }
}
