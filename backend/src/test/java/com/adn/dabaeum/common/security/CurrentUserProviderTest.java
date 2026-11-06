package com.adn.dabaeum.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsPrincipalUserId() {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUserPrincipal(id, Set.of("ROLE_USER")),
                null,
                List.of()
            )
        );

        assertThat(provider.requireUserId()).isEqualTo(id);
    }

    @Test
    void returnsAuthenticatedContextAndKeepsUserIdCompatibility() {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUserPrincipal(
                    id,
                    "DADAEGU",
                    Set.of("ROLE_USER")
                ),
                null,
                List.of()
            )
        );

        assertThat(provider.requireContext().userId()).isEqualTo(id);
        assertThat(provider.requireContext().provider()).isEqualTo("DADAEGU");
        assertThat(provider.requireUserId()).isEqualTo(id);
    }

    /** 공개 조회는 로그인하지 않은 요청도 받는다. 그때는 문맥이 없다고 답해야 한다. */
    @Test
    void findsNoContextForAnonymousOrMissingAuthentication() {
        assertThat(provider.findContext()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(provider.findContext()).isEmpty();
    }

    @Test
    void findsContextWhenAuthenticated() {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUserPrincipal(id, Set.of("ROLE_USER")), null, List.of()));

        assertThat(provider.findContext()).get()
            .extracting(AuthenticatedUserContext::userId).isEqualTo(id);
    }

    @Test
    void rejectsDevStringPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "local-platform-admin",
                null,
                List.of()
            )
        );

        assertThatThrownBy(provider::requireUserId)
            .isInstanceOf(com.adn.dabaeum.common.api.ApiException.class)
            .extracting("status")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void prefersScopedContextFromAuthenticatedTokenDetails() {
        UUID userId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        AuthenticatedUserContext context = new AuthenticatedUserContext(
            userId,
            "DADAEGU",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", institutionId))
        );
        UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUserPrincipal(userId, Set.of("INSTITUTION_ADMIN")),
                null,
                List.of()
            );
        authentication.setDetails(new AuthenticatedTokenDetails(
            java.time.Instant.parse("2099-01-01T00:00:00Z"),
            context
        ));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(provider.requireContext()).isEqualTo(context);
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(provider::requireUserId)
            .isInstanceOf(com.adn.dabaeum.common.api.ApiException.class)
            .extracting("status")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
