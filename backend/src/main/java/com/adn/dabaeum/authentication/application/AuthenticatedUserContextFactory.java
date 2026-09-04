package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.identity.domain.IdentityProvider;
import com.adn.dabaeum.identity.domain.UserIdentityRepository;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserContextFactory {

    private final UserIdentityRepository identityRepository;
    private final UserRoleRepository roleRepository;

    public AuthenticatedUserContextFactory(
        UserIdentityRepository identityRepository,
        UserRoleRepository roleRepository
    ) {
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
    }

    public AuthenticatedUserContext create(
        IdentityProvider provider,
        String providerSubject
    ) {
        UUID userId = identityRepository.findByProviderSubject(
                provider,
                providerSubject
            )
            .map(identity -> identity.userId())
            .orElseThrow(this::identityNotFound);

        List<AuthenticatedRole> roles = roleRepository.findByUserId(userId)
            .stream()
            .map(assignment -> new AuthenticatedRole(
                assignment.role().name(),
                assignment.institutionId()
            ))
            .toList();

        return new AuthenticatedUserContext(
            userId,
            provider.name(),
            java.util.Set.copyOf(roles)
        );
    }

    private ApiException identityNotFound() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.IDENTITY_NOT_FOUND,
            "Identity not found"
        );
    }
}
