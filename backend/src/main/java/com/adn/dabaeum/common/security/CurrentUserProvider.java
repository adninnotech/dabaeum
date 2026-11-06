package com.adn.dabaeum.common.security;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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

    /**
     * 로그인하지 않은 요청도 받는 공개 조회용. 인증이 없거나 익명이면 비어 있다.
     * 인증은 됐는데 문맥을 만들 수 없는 경우(dev 문자열 principal 등)는 requireContext 와 같이 401 이다.
     */
    public Optional<AuthenticatedUserContext> findContext() {
        Authentication authentication = SecurityContextHolder
            .getContext()
            .getAuthentication();
        if (authentication == null
            || authentication instanceof AnonymousAuthenticationToken
            || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.of(requireContext());
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
