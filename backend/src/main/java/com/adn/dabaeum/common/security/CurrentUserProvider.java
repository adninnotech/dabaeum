package com.adn.dabaeum.common.security;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUserProvider {

    public AuthenticatedUserContext requireContext() {
        Authentication authentication = SecurityContextHolder
            .getContext()
            .getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw unauthorized();
        }
        if (authentication.getDetails() instanceof AuthenticatedTokenDetails details
            && details.context() != null) {
            return details.context();
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthenticatedUserPrincipal user)) {
            throw unauthorized();
        }
        return user.toContext();
    }

    public UUID requireUserId() {
        return requireContext().userId();
    }

    private ApiException unauthorized() {
        return new ApiException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.UNAUTHORIZED,
            "Authentication required"
        );
    }
}
