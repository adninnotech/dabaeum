package com.adn.dabaeum.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.identity.domain.UserIdentity;
import com.adn.dabaeum.identity.domain.UserIdentityRepository;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserContextFactoryTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID ROLE_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock
    UserIdentityRepository identityRepository;

    @Mock
    UserRoleRepository roleRepository;

    private AuthenticatedUserContextFactory factory;

    @BeforeEach
    void setUp() {
        factory = new AuthenticatedUserContextFactory(
            identityRepository,
            roleRepository
        );
    }

    @Test
    void mapsProviderIdentityAndScopedRolesWithoutRawClaims() {
        when(identityRepository.findByProviderSubject(
            IdentityProvider.DADAEGU,
            "provider-subject"
        )).thenReturn(Optional.of(new UserIdentity(
            UUID.randomUUID(),
            USER_ID,
            IdentityProvider.DADAEGU,
            "provider-subject",
            "did:example:public",
            NOW,
            "{\"private\":true}",
            NOW,
            NOW
        )));
        when(roleRepository.findByUserId(USER_ID)).thenReturn(List.of(
            new UserRoleAssignment(
                ROLE_ID,
                USER_ID,
                INSTITUTION_ID,
                UserRole.INSTRUCTOR,
                NOW
            )
        ));

        var context = factory.create(
            IdentityProvider.DADAEGU,
            "provider-subject"
        );

        assertThat(context.userId()).isEqualTo(USER_ID);
        assertThat(context.provider()).isEqualTo("DADAEGU");
        assertThat(context.roles())
            .containsExactly(new com.adn.dabaeum.common.security.AuthenticatedRole(
                "INSTRUCTOR",
                INSTITUTION_ID
            ));
    }

    @Test
    void rejectsProviderSubjectWithoutStoredIdentity() {
        when(identityRepository.findByProviderSubject(
            IdentityProvider.DID,
            "missing-subject"
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> factory.create(
            IdentityProvider.DID,
            "missing-subject"
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.code()).isEqualTo(ApiErrorCode.IDENTITY_NOT_FOUND);
            assertThat(exception.getMessage()).doesNotContain("missing-subject");
        });
    }
}
