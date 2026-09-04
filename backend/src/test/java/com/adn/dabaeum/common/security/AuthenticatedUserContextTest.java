package com.adn.dabaeum.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.common.api.ApiErrorCode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticatedUserContextTest {

    private static final UUID USER_ID = UUID.fromString(
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    );

    @Test
    void mapsLegacyPrincipalToLocalContextWithoutChangingRoles() {
        AuthenticatedUserPrincipal principal =
            new AuthenticatedUserPrincipal(USER_ID, Set.of("ROLE_USER"));

        AuthenticatedUserContext context = principal.toContext();

        assertThat(context.userId()).isEqualTo(USER_ID);
        assertThat(context.provider()).isEqualTo("LOCAL");
        assertThat(context.roles()).containsExactly(
            new AuthenticatedRole("ROLE_USER", null)
        );
    }

    @Test
    void preservesProviderAndScopedRoles() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
            USER_ID,
            "DADAEGU",
            Set.of("ROLE_PLATFORM_ADMIN")
        );

        assertThat(principal.toContext().provider()).isEqualTo("DADAEGU");
        assertThat(principal.toContext().roles()).containsExactly(
            new AuthenticatedRole("ROLE_PLATFORM_ADMIN", null)
        );

        AuthenticatedUserContext context = new AuthenticatedUserContext(
            USER_ID,
            "DID",
            Set.of(new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID))
        );
        assertThat(context.roles()).containsExactly(
            new AuthenticatedRole("INSTRUCTOR", INSTITUTION_ID)
        );
    }

    @Test
    void copiesRoleSetsAndRejectsInvalidContextValues() {
        Set<AuthenticatedRole> roles = new HashSet<>(Set.of(
            new AuthenticatedRole("LEARNER", INSTITUTION_ID)
        ));
        AuthenticatedUserContext context = new AuthenticatedUserContext(
            USER_ID,
            "DADAEGU",
            roles
        );

        assertThat(context.roles()).isNotSameAs(roles);
        assertThatThrownBy(() -> context.roles().add(
            new AuthenticatedRole("ROLE_ADMIN", null)
        )).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new AuthenticatedRole(" ", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticatedUserContext(
            null,
            "DADAEGU",
            Set.of()
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticatedUserContext(
            USER_ID,
            " ",
            Set.of()
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticatedUserContext(
            USER_ID,
            "DADAEGU",
            null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void declaresStage3ErrorCodesForLaterAdapters() {
        assertThat(ApiErrorCode.valueOf("AUTHENTICATION_FAILED")).isNotNull();
        assertThat(ApiErrorCode.valueOf("IDENTITY_NOT_FOUND")).isNotNull();
        assertThat(ApiErrorCode.valueOf("IDENTITY_CONFLICT")).isNotNull();
        assertThat(ApiErrorCode.valueOf("IDENTITY_REQUIRED")).isNotNull();
        assertThat(ApiErrorCode.valueOf("IDENTITY_NOT_VERIFIED")).isNotNull();
        assertThat(ApiErrorCode.valueOf("ROLE_NOT_FOUND")).isNotNull();
        assertThat(ApiErrorCode.valueOf("ROLE_ASSIGNMENT_CONFLICT")).isNotNull();
        assertThat(ApiErrorCode.valueOf("ROLE_REQUIRED")).isNotNull();
        assertThat(ApiErrorCode.valueOf("USER_STATUS_FORBIDDEN")).isNotNull();
    }
}
