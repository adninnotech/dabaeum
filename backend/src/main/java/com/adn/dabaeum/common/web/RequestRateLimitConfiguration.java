package com.adn.dabaeum.common.web;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Clock;
import java.util.Map;
import org.springframework.util.AntPathMatcher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
public class RequestRateLimitConfiguration implements WebMvcConfigurer {

    private record Limit(int count, Duration window) {}

    private static final Map<String, Limit> LIMITS = Map.of(
        "POST /api/v1/auth/login", new Limit(20, Duration.ofMinutes(1)),
        "POST /api/v1/auth/dadaegu/login", new Limit(20, Duration.ofMinutes(1)),
        "POST /api/v1/auth/signup", new Limit(10, Duration.ofHours(1)),
        "POST /api/v1/auth/dadaegu/signup", new Limit(10, Duration.ofHours(1)),
        "GET /api/v1/auth/email/availability", new Limit(30, Duration.ofMinutes(1)),
        "POST /api/v1/auth/password/reset-request", new Limit(10, Duration.ofHours(1)),
        "POST /api/v1/auth/password/reset", new Limit(10, Duration.ofMinutes(1)),
        "POST /api/v1/credentials/verify", new Limit(30, Duration.ofMinutes(1)),
        // 이용 통계 방문 기록. 하루 1건이라 정상 사용은 몇 번이면 끝난다. 키를 바꿔 부풀리는 것만 막는다.
        "POST /api/v1/visits", new Limit(60, Duration.ofMinutes(1)),
        // 통합검색은 이용 통계 검색 기록을 쓴다(docs/specs/usage-statistics.md §4.2). 기관 공용 IP 를 생각해 넉넉히 둔다.
        "GET /api/v1/search", new Limit(120, Duration.ofMinutes(1))
    );

    /** 경로 변수가 있는 경로. 카운터 키는 패턴이라 변수 값(기관 id 등)을 바꿔도 같은 IP 면 함께 센다. */
    private static final Map<String, Limit> PATTERN_LIMITS = Map.of(
        "POST /api/v1/institutions/*/learners/lookup", new Limit(30, Duration.ofMinutes(1)),
        // 공개 NFT 메타데이터. 탐색기·지갑은 한 출처에서 여러 토큰을 몰아 읽으므로 넉넉히 두고, 전수 대입만 막는다.
        "GET /api/v1/public/nft/badges/*", new Limit(300, Duration.ofMinutes(1)),
        "GET /api/v1/public/nft/certificates/*", new Limit(300, Duration.ofMinutes(1)),
        "POST /api/v1/banners/*/clicks", new Limit(60, Duration.ofMinutes(1))
    );

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    @Bean
    RequestRateLimiter requestRateLimiter() {
        return limiter;
    }

    private final RequestRateLimiter limiter;

    public RequestRateLimitConfiguration(Clock clock) {
        this.limiter = new RequestRateLimiter(clock);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                Object handler) {
                return check(request, response);
            }
        });
    }

    boolean check(HttpServletRequest request, HttpServletResponse response) {
        String route = request.getMethod() + " " + request.getRequestURI();
        Limit limit = LIMITS.get(route);
        if (limit == null) {
            route = PATTERN_LIMITS.keySet().stream()
                .filter(pattern -> PATHS.match(pattern, request.getMethod() + " " + request.getRequestURI()))
                .findFirst().orElse(null);
            if (route == null) return true;
            limit = PATTERN_LIMITS.get(route);
        }
        // Tomcat resolves trusted proxy headers before this point; never parse headers here.
        long retry = limiter.allow(route + ":" + request.getRemoteAddr(),
            limit.count(), limit.window());
        if (retry > 0) {
            response.setHeader("Retry-After", Long.toString(retry));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                ApiErrorCode.RATE_LIMITED, "Too many requests");
        }
        return true;
    }
}
