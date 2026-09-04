package com.adn.dabaeum.common.config;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * URL 기반 인가 규칙표.
 *
 * <p>규칙은 위에서 아래로 처음 맞는 것이 적용된다. 한 메서드에 나열하던 것을 도메인별로 나눠
 * 선언하고, {@code ApiAuthorizationRuleCoverageTest} 가 OpenAPI 의 모든 operation 에 명시적
 * 규칙이 있는지 검사한다. 규칙이 빠진 엔드포인트는 {@code anyRequest().authenticated()} 로
 * 떨어져 로그인만 하면 누구나 호출할 수 있으므로, 새 엔드포인트는 반드시 여기에 추가한다.
 */
public final class ApiAuthorizationRules {

    public enum Access { PERMIT_ALL, AUTHENTICATED, ROLES }

    /** method 가 null 이면 모든 메서드에 적용된다. */
    public record Rule(HttpMethod method, List<String> patterns, Access access, List<String> roles) {

        static Rule permitAll(HttpMethod method, String... patterns) {
            return new Rule(method, List.of(patterns), Access.PERMIT_ALL, List.of());
        }

        static Rule authenticated(HttpMethod method, String... patterns) {
            return new Rule(method, List.of(patterns), Access.AUTHENTICATED, List.of());
        }

        static Rule roles(HttpMethod method, List<String> roles, String... patterns) {
            return new Rule(method, List.of(patterns), Access.ROLES, roles);
        }
    }

    private static final List<String> PLATFORM_ADMIN = List.of("PLATFORM_ADMIN");
    private static final List<String> MANAGERS = List.of("PLATFORM_ADMIN", "INSTITUTION_ADMIN");
    private static final List<String> MANAGERS_AND_INSTRUCTOR =
        List.of("PLATFORM_ADMIN", "INSTITUTION_ADMIN", "INSTRUCTOR");
    private static final List<String> LEARNER = List.of("LEARNER");

    private static final HttpMethod GET = HttpMethod.GET;
    private static final HttpMethod POST = HttpMethod.POST;
    private static final HttpMethod PUT = HttpMethod.PUT;
    private static final HttpMethod PATCH = HttpMethod.PATCH;
    private static final HttpMethod DELETE = HttpMethod.DELETE;

    private ApiAuthorizationRules() {
    }

    public static List<Rule> rules() {
        return RULES;
    }

    static void apply(
        AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        for (Rule rule : RULES) {
            String[] patterns = rule.patterns().toArray(String[]::new);
            var matcher = rule.method() == null
                ? auth.requestMatchers(patterns)
                : auth.requestMatchers(rule.method(), patterns);
            switch (rule.access()) {
                case PERMIT_ALL -> matcher.permitAll();
                case AUTHENTICATED -> matcher.authenticated();
                case ROLES -> matcher.hasAnyRole(rule.roles().toArray(String[]::new));
            }
        }
    }

    private static final List<Rule> RULES = List.of(
        // ---- 공개·시스템 ----
        Rule.permitAll(null, "/api/v1/system/ping", "/actuator/health"),
        Rule.permitAll(POST,
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/dadaegu/login",
            "/api/v1/auth/password/reset-request",
            "/api/v1/auth/password/reset"),
        Rule.permitAll(GET, "/api/v1/auth/email/availability"),
        Rule.permitAll(GET, "/api/v1/auth/dadaegu/config"),
        Rule.permitAll(GET, "/api/v1/files/*/content"),
        Rule.authenticated(POST, "/api/v1/files"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/users/*/password/reset"),
        Rule.permitAll(null,
            "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/openapi/**", "/api-guide/**"),
        Rule.permitAll(GET,
            "/api/v1/vc/contexts/lifelong-education/*",
            "/api/v1/vc/vocabulary/lifelong-education/*",
            "/api/v1/vc/issuers/*",
            "/api/v1/vc/status/*",
            "/api/v1/vc/status-lists/*"),

        // ---- 지원(공지·FAQ·약관·코드) ----
        Rule.permitAll(GET,
            "/api/v1/notices", "/api/v1/notices/*", "/api/v1/support/faqs",
            "/api/v1/contents/terms", "/api/v1/codes"),
        Rule.roles(null, PLATFORM_ADMIN, "/api/v1/admin/notices", "/api/v1/admin/notices/*"),

        // ---- 대시보드·모니터링 ----
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/admin/dashboard"),
        Rule.roles(GET, PLATFORM_ADMIN,
            "/api/v1/blockchain/metrics", "/api/v1/blockchain/transactions",
            "/api/v1/blockchain/alerts"),
        Rule.roles(GET, MANAGERS, "/api/v1/institutions/*/dashboard"),
        Rule.roles(PATCH, MANAGERS, "/api/v1/institutions/*/instructors/*"),

        // ---- 기관 가입 신청 ----
        Rule.authenticated(POST, "/api/v1/institution-applications"),
        Rule.authenticated(GET,
            "/api/v1/institution-applications", "/api/v1/institution-applications/*"),
        Rule.roles(POST, PLATFORM_ADMIN,
            "/api/v1/institution-applications/*/approve",
            "/api/v1/institution-applications/*/reject"),

        // ---- 문의·수강평·관심·알림 ----
        Rule.authenticated(POST, "/api/v1/inquiries"),
        Rule.authenticated(GET,
            "/api/v1/inquiries/*", "/api/v1/users/me/inquiries", "/api/v1/users/me/reviews",
            "/api/v1/users/me/interests", "/api/v1/users/me/notifications",
            "/api/v1/courses/*/reviews"),
        Rule.roles(GET, MANAGERS_AND_INSTRUCTOR, "/api/v1/instructors/me/inquiries"),
        Rule.roles(GET, MANAGERS, "/api/v1/institutions/*/inquiries"),
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR, "/api/v1/inquiries/*/reply"),
        Rule.roles(POST, LEARNER, "/api/v1/courses/*/reviews"),
        Rule.authenticated(POST, "/api/v1/users/me/interests"),
        Rule.authenticated(DELETE, "/api/v1/users/me/interests/*"),
        Rule.authenticated(POST, "/api/v1/users/me/notifications/*/read"),

        // ---- 기관·과정·강사 ----
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/institutions"),
        Rule.roles(PUT, PLATFORM_ADMIN, "/api/v1/institutions/*"),
        Rule.roles(POST, MANAGERS, "/api/v1/courses"),
        Rule.roles(POST, MANAGERS, "/api/v1/courses/*/instructors"),
        Rule.roles(PUT, MANAGERS, "/api/v1/courses/*/instructors/*"),
        Rule.roles(DELETE, MANAGERS, "/api/v1/courses/*/instructors/*"),
        Rule.authenticated(GET, "/api/v1/courses/*/instructors"),
        Rule.roles(GET, MANAGERS, "/api/v1/institutions/*/instructor-applications"),
        Rule.roles(POST, MANAGERS,
            "/api/v1/instructor-applications/*/approve",
            "/api/v1/instructor-applications/*/reject"),
        Rule.authenticated(POST, "/api/v1/institutions/*/instructor-applications"),
        Rule.authenticated(GET,
            "/api/v1/instructor-applications/me", "/api/v1/instructor-applications/*"),
        Rule.roles(PUT, MANAGERS, "/api/v1/courses/*"),
        Rule.roles(POST, MANAGERS, "/api/v1/courses/*/publish", "/api/v1/courses/*/close"),
        // 정정 API: 되돌릴 수 없는 전이를 사유와 함께 되돌린다. 플랫폼 관리자만.
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/courses/*/reopen"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/sessions/*/reopen"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/enrollments/*/completion/revert"),

        // ---- 출결 ----
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR, "/api/v1/sessions/*/qr-token"),
        Rule.roles(POST, List.of("PLATFORM_ADMIN", "INSTITUTION_ADMIN", "INSTRUCTOR", "LEARNER"),
            "/api/v1/sessions/*/attendance"),
        Rule.authenticated(GET, "/api/v1/sessions/*/attendance", "/api/v1/attendance/*"),
        Rule.roles(PATCH, MANAGERS_AND_INSTRUCTOR, "/api/v1/attendance/*"),
        Rule.authenticated(GET, "/api/v1/enrollments/*/attendance-summary"),

        // ---- 이수·수료증 ----
        Rule.authenticated(GET, "/api/v1/enrollments/*/completion"),
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR,
            "/api/v1/enrollments/*/completion/evaluate",
            "/api/v1/enrollments/*/completion/confirm"),
        Rule.roles(POST, MANAGERS, "/api/v1/completions/*/credentials"),
        Rule.permitAll(POST, "/api/v1/credentials/verify"),
        Rule.roles(POST, MANAGERS,
            "/api/v1/credentials/*/revoke", "/api/v1/credentials/*/reissue"),
        Rule.roles(POST, MANAGERS, "/api/v1/credentials/*/badges"),
        Rule.authenticated(GET, "/api/v1/badges/*"),
        Rule.authenticated(GET, "/api/v1/users/*/badges"),
        Rule.authenticated(GET, "/api/v1/credentials/*"),
        Rule.authenticated(GET, "/api/v1/credentials/*/document"),
        Rule.authenticated(GET, "/api/v1/credentials/*/verifications"),
        Rule.authenticated(GET, "/api/v1/users/me/credentials"),
        Rule.authenticated(GET, "/api/v1/users/*/credentials"),

        // ---- 회차·수강신청 ----
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR, "/api/v1/courses/*/sessions"),
        Rule.roles(PUT, MANAGERS_AND_INSTRUCTOR, "/api/v1/sessions/*"),
        Rule.roles(POST, LEARNER, "/api/v1/courses/*/enrollments"),
        Rule.roles(POST, MANAGERS, "/api/v1/courses/*/proxy-enrollments"),
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR,
            "/api/v1/enrollments/*/approve", "/api/v1/enrollments/*/reject"),
        Rule.authenticated(POST,
            "/api/v1/enrollments/*/cancel", "/api/v1/enrollments/*/withdraw"),
        Rule.authenticated(GET,
            "/api/v1/courses", "/api/v1/courses/*", "/api/v1/courses/*/sessions",
            "/api/v1/courses/*/enrollments", "/api/v1/sessions/*"),
        Rule.authenticated(GET, "/api/v1/enrollments/*"),
        Rule.authenticated(GET,
            "/api/v1/users/me/enrollments", "/api/v1/users/me/learning-summary",
            "/api/v1/users/me/learning-courses", "/api/v1/enrollments/*/progress"),
        Rule.roles(GET, MANAGERS_AND_INSTRUCTOR, "/api/v1/instructors/me/enrollment-status"),
        Rule.roles(GET, MANAGERS_AND_INSTRUCTOR,
            "/api/v1/instructors/me/courses", "/api/v1/instructors/me/courses/stats"),
        Rule.roles(GET, MANAGERS,
            "/api/v1/institutions/*/courses", "/api/v1/institutions/*/enrollments",
            "/api/v1/institutions/*/instructors"),

        // ---- 사용자·인증 ----
        Rule.authenticated(GET, "/api/v1/users/me"),
        Rule.authenticated(PUT, "/api/v1/users/me"),
        Rule.authenticated(GET, "/api/v1/auth/session"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/users"),
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/users"),
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/users/*"),
        Rule.roles(PUT, PLATFORM_ADMIN, "/api/v1/users/*"),
        Rule.roles(PATCH, PLATFORM_ADMIN, "/api/v1/users/*/status"),
        Rule.authenticated(POST,
            "/api/v1/users/me/identities/dadaegu", "/api/v1/users/me/identities/local"),
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/users/*/identities"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/users/*/identities"),
        Rule.roles(DELETE, PLATFORM_ADMIN, "/api/v1/users/*/identities/*"),
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/users/*/roles"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/users/*/roles"),
        Rule.roles(DELETE, PLATFORM_ADMIN, "/api/v1/users/*/roles/*"),
        Rule.authenticated(GET, "/api/v1/institutions", "/api/v1/institutions/*")
    );
}
