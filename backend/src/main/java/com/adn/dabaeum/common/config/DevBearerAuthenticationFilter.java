package com.adn.dabaeum.common.config;

import com.adn.dabaeum.common.security.AuthenticatedUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class DevBearerAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    // 개발용 고정 플랫폼 관리자 신원.
    // 실제 사용자 컨텍스트(CurrentUserProvider.requireContext)가 필요한 엔드포인트를 위해
    // 문자열이 아닌 AuthenticatedUserPrincipal 을 부여한다.
    // V17 이 같은 id 로 시스템 계정 행을 넣어 두므로 감사 컬럼의 FK 도 만족한다.
    // 실제 사용자 id 와 겹치지 않도록 예약 대역(ffffffff-…)을 쓴다.
    static final UUID DEV_PLATFORM_ADMIN_ID =
        UUID.fromString("ffffffff-0000-0000-0000-000000000001");

    private final DevBearerTokenProperties properties;

    public DevBearerAuthenticationFilter(DevBearerTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(AUTHORIZATION);
        if (authorization != null
            && authorization.regionMatches(
                true,
                0,
                BEARER_PREFIX,
                0,
                BEARER_PREFIX.length()
            )) {
            String candidate = authorization.substring(BEARER_PREFIX.length())
                .trim();
            if (properties.matches(candidate)) {
                AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                    DEV_PLATFORM_ADMIN_ID,
                    "LOCAL",
                    Set.of("PLATFORM_ADMIN")
                );
                SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
                    )
                );
            }
        }

        filterChain.doFilter(request, response);
    }
}
