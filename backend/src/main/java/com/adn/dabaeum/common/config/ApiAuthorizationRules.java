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
            "/api/v1/auth/dadaegu/signup",
            "/api/v1/auth/password/reset-request",
            "/api/v1/auth/password/reset"),
        Rule.permitAll(GET, "/api/v1/auth/email/availability"),
        Rule.permitAll(GET, "/api/v1/auth/dadaegu/config"),
        Rule.permitAll(GET, "/api/v1/files/*/content"),
        Rule.authenticated(POST, "/api/v1/files"),
        Rule.authenticated(PUT, "/api/v1/users/me/password"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/users/*/password/reset"),
        Rule.permitAll(null,
            "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/openapi/**", "/api-guide/**"),
        // 학생 폰이 강사 QR 로 여는 페이지와 그 결과 조회. 로그인하지 않는다 —
        // 본인 확인은 다대구 DID 가, 현장 확인은 QR 토큰이 한다.
        Rule.permitAll(GET, "/attend", "/attend/*/status"),
        // 같은 흐름을 프론트 학생 페이지가 JSON 으로 부른다. 전환이 끝나면 위 HTML 경로는 지운다.
        Rule.permitAll(POST, "/api/v1/attend-sessions"),
        Rule.permitAll(GET, "/api/v1/attend-sessions/*"),
        Rule.permitAll(GET,
            "/api/v1/vc/contexts/lifelong-education/*",
            "/api/v1/vc/vocabulary/lifelong-education/*",
            "/api/v1/vc/issuers/*",
            "/api/v1/vc/status/*",
            "/api/v1/vc/status-lists/*"),
        // 대구체인 NFT 의 uri 가 가리키는 메타데이터. 체인에서 NFT 를 본 누구나 연다(개인정보 없음).
        Rule.permitAll(GET, "/api/v1/public/nft/badges/*", "/api/v1/public/nft/certificates/*"),

        // ---- 지원(공지·FAQ·약관·코드) ----
        Rule.permitAll(GET,
            "/api/v1/notices", "/api/v1/notices/*", "/api/v1/notices/*/attachments", "/api/v1/support/faqs",
            "/api/v1/contents/terms", "/api/v1/codes"),
        // E-1 홈 최상단 캐러셀. 비로그인 방문자도 본다.
        Rule.permitAll(GET, "/api/v1/banners"),
        // 강좌 찾기·상세 화면은 로그인 전에도 연다. DRAFT 는 서비스가 가리고(비로그인 = 학습자와 같은
        // 기준), 강사 이메일은 비로그인 응답에서 뺀다. 신청·문의·첨부·회차 단건은 계속 로그인이 필요하다.
        // 통합검색(설계서 §5). 공개.
        Rule.permitAll(GET, "/api/v1/search"),
        // 강좌 상세 조회 기록(설계서 §8.1). 비로그인도 센다.
        Rule.permitAll(POST, "/api/v1/courses/*/views"),
        // 방문·배너 클릭 기록(docs/specs/usage-statistics.md §4). 비로그인도 센다.
        Rule.permitAll(POST, "/api/v1/visits", "/api/v1/banners/*/clicks"),
        // 홈 추천 강좌(docs/specs/course-recommendation.md §6). 조회는 무인증(로그인하면 개인화), 클릭 로그는 학습자.
        Rule.permitAll(GET, "/api/v1/courses/recommended"),
        Rule.roles(POST, LEARNER, "/api/v1/courses/recommended/*/click"),
        Rule.permitAll(GET,
            "/api/v1/courses", "/api/v1/courses/*", "/api/v1/courses/*/sessions",
            "/api/v1/courses/*/instructors",
            "/api/v1/courses/*/reviews", "/api/v1/courses/*/reviews/summary"),
        Rule.roles(null, PLATFORM_ADMIN, "/api/v1/admin/notices", "/api/v1/admin/notices/*"),
        Rule.roles(null, PLATFORM_ADMIN,
            "/api/v1/admin/notices/*/attachments", "/api/v1/admin/notices/*/attachments/*"),
        Rule.roles(null, PLATFORM_ADMIN, "/api/v1/admin/faqs", "/api/v1/admin/faqs/*"),
        Rule.roles(null, PLATFORM_ADMIN, "/api/v1/admin/terms"),

        // ---- 표준 강좌 분류(설계서 §12). 코드는 마이그레이션이 정하고 관리자는 사용 여부만 바꾼다 ----
        Rule.permitAll(GET, "/api/v1/course-classifications"),
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/admin/course-classifications"),
        Rule.roles(POST, PLATFORM_ADMIN,
            "/api/v1/admin/course-classifications/*/enable",
            "/api/v1/admin/course-classifications/*/disable"),

        // ---- 학습배지 정의(설계서 §11). 도감은 공개, 관리는 플랫폼 관리자 ----
        Rule.permitAll(GET, "/api/v1/badge-definitions"),
        Rule.roles(null, PLATFORM_ADMIN,
            "/api/v1/admin/badge-definitions", "/api/v1/admin/badge-definitions/*",
            "/api/v1/admin/badge-definitions/*/activate", "/api/v1/admin/badge-definitions/*/deactivate"),
        // NFT 학습배지 현황(설계서 §11.4). 관리자 검색. 조회는 관리자 접속기록에 남는다.
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/admin/badges"),
        // 관리자 배지 폐기(사유 필수). 플랫폼 관리자만.
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/admin/badges/*/revoke"),

        // ---- CMS 메뉴·콘텐츠(설계서 §7). 공개 트리·게시 페이지는 무인증, 관리는 플랫폼 관리자 ----
        Rule.permitAll(GET, "/api/v1/menus", "/api/v1/contents/pages/*"),
        Rule.roles(null, PLATFORM_ADMIN,
            "/api/v1/admin/content-pages", "/api/v1/admin/content-pages/*",
            "/api/v1/admin/content-pages/*/publish", "/api/v1/admin/content-pages/*/unpublish",
            "/api/v1/admin/menus", "/api/v1/admin/menus/*"),

        // ---- 공공데이터 평생학습강좌(docs/specs/public-lifelong-courses.md §5). 조회는 무인증, 수집은 플랫폼 관리자 ----
        Rule.permitAll(GET, "/api/v1/public-courses", "/api/v1/public-courses/*"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/admin/public-courses/sync"),
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/admin/public-course-sync-runs", "/api/v1/admin/public-course-sync-runs/*"),

        // ---- 배너(B-10) ----
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/admin/banners"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/admin/banners"),
        Rule.roles(PUT, PLATFORM_ADMIN, "/api/v1/admin/banners/*"),
        Rule.roles(DELETE, PLATFORM_ADMIN, "/api/v1/admin/banners/*"),

        // ---- 사이트 전체 문의·수강 조회(B-7·수강현황관리) ----
        // /institutions/{id}/* 는 기관 단위라 오너가 전 기관을 못 본다. /admin/* 는
        // PLATFORM_ADMIN 전용이며, 기관 관리자는 여전히 /institutions/{id}/* 를 쓴다.
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/admin/inquiries", "/api/v1/admin/enrollments"),

        // ---- 대시보드·모니터링 ----
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/admin/dashboard"),

        // ---- 관리자 접속기록(설계서 §10.2). 조회만, 수정·삭제 API 는 없다 ----
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/admin/access-logs"),
        // ---- 관리자 통계(설계서 §9). JSON·CSV 같은 경로 ----
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/admin/statistics/enrollments", "/api/v1/admin/statistics/badges"),
        // 이용 통계(docs/specs/usage-statistics.md §6).
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/admin/statistics/usage", "/api/v1/admin/statistics/usage/*"),
        // ---- 관리자 계정(설계서 §10.1). 역할·상태 변경은 /users/{id}/roles·status 를 쓴다 ----
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/admin/administrators"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/admin/administrators"),
        Rule.roles(GET, PLATFORM_ADMIN,
            "/api/v1/blockchain/metrics", "/api/v1/blockchain/transactions",
            "/api/v1/blockchain/alerts"),
        Rule.roles(GET, MANAGERS, "/api/v1/institutions/*/dashboard"),
        Rule.roles(PATCH, MANAGERS, "/api/v1/institutions/*/instructors/*"),

        // ---- 기관 가입 신청 ----
        Rule.authenticated(POST, "/api/v1/institution-applications"),
        Rule.authenticated(GET,
            "/api/v1/institution-applications", "/api/v1/institution-applications/me",
            "/api/v1/institution-applications/*"),
        Rule.roles(POST, PLATFORM_ADMIN,
            "/api/v1/institution-applications/*/approve",
            "/api/v1/institution-applications/*/reject"),

        // ---- 문의·수강평·관심·알림 ----
        Rule.authenticated(POST, "/api/v1/inquiries"),
        Rule.authenticated(GET,
            "/api/v1/inquiries/*", "/api/v1/users/me/inquiries", "/api/v1/users/me/reviews",
            "/api/v1/users/me/reviews/summary",
            "/api/v1/users/me/interests", "/api/v1/users/me/notifications",
            "/api/v1/courses/*/inquiries"),
        Rule.roles(GET, MANAGERS_AND_INSTRUCTOR, "/api/v1/instructors/me/inquiries"),
        Rule.roles(GET, MANAGERS, "/api/v1/institutions/*/inquiries"),
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR, "/api/v1/inquiries/*/reply"),
        Rule.roles(POST, LEARNER, "/api/v1/courses/*/reviews"),
        Rule.authenticated(POST, "/api/v1/users/me/interests"),
        Rule.authenticated(DELETE, "/api/v1/users/me/interests/*"),
        Rule.authenticated(POST, "/api/v1/users/me/notifications/*/read"),

        // ---- 기관·과정·강사 ----
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/institutions"),
        Rule.roles(PUT, MANAGERS, "/api/v1/institutions/*"),
        Rule.roles(null, PLATFORM_ADMIN,
            "/api/v1/institutions/*/signing-keys",
            "/api/v1/institutions/*/signing-keys/*",
            "/api/v1/institutions/*/signing-keys/*/*"),
        // 강좌 생성은 강사에게도 열려 있다(피그마 정합 작업의 제품 결정).
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR, "/api/v1/courses"),
        // 임시저장 강좌 삭제는 수정과 같다 — 기관 범위·MAIN 강사 여부는 서비스가 본다.
        Rule.roles(DELETE, MANAGERS_AND_INSTRUCTOR, "/api/v1/courses/*"),
        Rule.roles(POST, MANAGERS, "/api/v1/courses/*/instructors"),
        Rule.roles(PUT, MANAGERS, "/api/v1/courses/*/instructors/*"),
        Rule.roles(DELETE, MANAGERS, "/api/v1/courses/*/instructors/*"),
        Rule.authenticated(GET, "/api/v1/courses/*/attachments"),
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR, "/api/v1/courses/*/attachments"),
        Rule.roles(DELETE, MANAGERS_AND_INSTRUCTOR, "/api/v1/courses/*/attachments/*"),
        Rule.roles(GET, MANAGERS, "/api/v1/institutions/*/instructor-applications"),
        Rule.roles(POST, MANAGERS,
            "/api/v1/instructor-applications/*/approve",
            "/api/v1/instructor-applications/*/reject"),
        Rule.authenticated(POST, "/api/v1/institutions/*/instructor-applications"),
        Rule.authenticated(GET,
            "/api/v1/instructor-applications/me", "/api/v1/instructor-applications/*"),
        // 강사는 MAIN 배정 강좌만 — 서비스(requireCourseSessionManager)가 가린다.
        Rule.roles(PUT, MANAGERS_AND_INSTRUCTOR, "/api/v1/courses/*"),
        Rule.roles(POST, MANAGERS, "/api/v1/courses/*/publish", "/api/v1/courses/*/close"),
        // 정정 API: 되돌릴 수 없는 전이를 사유와 함께 되돌린다. 플랫폼 관리자만.
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/courses/*/reopen"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/sessions/*/reopen"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/enrollments/*/completion/revert"),

        // ---- 출결 ----
        // 다대구 DID 출결: 서버가 다대구 QR 을 받아 강사 화면에 띄우고 결과도 서버가 받는다(C안).
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR, "/api/v1/sessions/*/qr-token"),
        // 수동(ADMIN) 기록 전용. 학습자 일반 QR 은 닫혔다 — 다대구 회원은 /attend 경로로 찍는다.
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR, "/api/v1/sessions/*/attendance"),
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
        Rule.authenticated(GET, "/api/v1/badges/*", "/api/v1/badges/*/history"),
        Rule.authenticated(GET, "/api/v1/users/*/badges"),
        Rule.authenticated(GET, "/api/v1/credentials/*"),
        Rule.authenticated(GET, "/api/v1/credentials/*/document"),
        Rule.authenticated(GET, "/api/v1/credentials/*/verifications"),
        Rule.authenticated(GET, "/api/v1/users/me/credentials"),
        Rule.authenticated(GET, "/api/v1/users/*/credentials"),

        // ---- 회차·수강신청 ----
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR, "/api/v1/courses/*/sessions"),
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR, "/api/v1/courses/*/sessions/bulk"),
        Rule.roles(PUT, MANAGERS_AND_INSTRUCTOR, "/api/v1/courses/*/sessions/order"),
        Rule.roles(PUT, MANAGERS_AND_INSTRUCTOR, "/api/v1/sessions/*"),
        Rule.roles(POST, LEARNER, "/api/v1/courses/*/enrollments"),
        Rule.roles(POST, MANAGERS, "/api/v1/courses/*/proxy-enrollments"),
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR,
            "/api/v1/enrollments/*/approve", "/api/v1/enrollments/*/reject"),
        Rule.roles(POST, MANAGERS_AND_INSTRUCTOR, "/api/v1/enrollments/bulk-decision"),
        Rule.authenticated(POST,
            "/api/v1/enrollments/*/cancel", "/api/v1/enrollments/*/withdraw"),
        Rule.authenticated(GET, "/api/v1/courses/*/enrollments", "/api/v1/sessions/*"),
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
        // 대리 수강신청 대상 정확 일치 조회(중앙 #56). 개인정보를 본문에 싣는 조회라 POST. 기관 범위는 서비스가 requireCourseManager 로 본다.
        Rule.roles(POST, MANAGERS, "/api/v1/institutions/*/learners/lookup"),

        // ---- 사용자·인증 ----
        Rule.authenticated(GET, "/api/v1/users/me"),
        Rule.authenticated(PUT, "/api/v1/users/me"),
        Rule.authenticated(GET, "/api/v1/users/me/institutions"),
        // 학습포트폴리오(설계서 §6). 본인만. 공개 조회는 토큰으로 무인증.
        Rule.authenticated(GET, "/api/v1/users/me/portfolio", "/api/v1/users/me/portfolio/shares"),
        Rule.authenticated(POST, "/api/v1/users/me/portfolio/shares"),
        Rule.authenticated(DELETE, "/api/v1/users/me/portfolio/shares/*"),
        Rule.permitAll(GET, "/api/v1/public/portfolios/*"),
        Rule.authenticated(POST, "/api/v1/users/me/withdraw"),
        Rule.authenticated(PUT, "/api/v1/users/me/notification-preferences"),
        Rule.authenticated(GET, "/api/v1/auth/session"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/users"),
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/users"),
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/users/*"),
        Rule.roles(PUT, PLATFORM_ADMIN, "/api/v1/users/*"),
        Rule.roles(PATCH, PLATFORM_ADMIN, "/api/v1/users/*/status"),
        // B-4 회원 상세 연필 아이콘 — 사이트 전역 관리자 메모. /users/me/memo 는 없어 와일드카드가
        // me 를 삼킬 걱정이 없지만, 관례대로 아래 me 규칙들 뒤에 둔다.
        Rule.roles(PATCH, PLATFORM_ADMIN, "/api/v1/users/*/memo"),
        // B-4 회원 상세 — 남의 학습 이력 조회. 아래 와일드카드는 /users/me/... 도 삼키므로
        // 반드시 위의 모든 me 규칙 뒤에 둔다(규칙은 먼저 맞는 것이 이긴다).
        // /users/*/identities 가 /users/me/identities 를 삼켜 학습자가 403 을 받은 전례가 있다.
        Rule.roles(GET, PLATFORM_ADMIN,
            "/api/v1/users/*/enrollments", "/api/v1/users/*/learning-courses",
            "/api/v1/users/*/inquiries", "/api/v1/users/*/learning-stats"),
        Rule.roles(GET, PLATFORM_ADMIN,
            "/api/v1/instructors/*/courses", "/api/v1/instructors/*/courses/stats",
            "/api/v1/instructors/*/inquiries", "/api/v1/instructors/*/enrollment-status"),
        Rule.authenticated(POST,
            "/api/v1/users/me/identities/dadaegu", "/api/v1/users/me/identities/local"),
        Rule.authenticated(GET, "/api/v1/users/me/identities"),
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/users/*/identities"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/users/*/identities"),
        Rule.roles(DELETE, PLATFORM_ADMIN, "/api/v1/users/*/identities/*"),
        Rule.roles(GET, PLATFORM_ADMIN, "/api/v1/users/*/roles"),
        Rule.roles(POST, PLATFORM_ADMIN, "/api/v1/users/*/roles"),
        Rule.roles(DELETE, PLATFORM_ADMIN, "/api/v1/users/*/roles/*"),
        Rule.authenticated(GET, "/api/v1/institutions", "/api/v1/institutions/*")
    );
}
