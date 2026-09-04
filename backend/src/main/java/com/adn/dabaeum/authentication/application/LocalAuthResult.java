package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record LocalAuthResult(
    String accessToken,
    String tokenType,
    long expiresIn,
    UUID userId,
    String provider,
    List<AuthenticatedRole> roles,
    Instant expiresAt
) {

    public LocalAuthResult {
        roles = List.copyOf(roles);
    }

    public static LocalAuthResult of(
        IssuedAccessToken token,
        UUID userId,
        String provider,
        List<UserRoleAssignment> assignments,
        Clock clock
    ) {
        List<AuthenticatedRole> roles = assignments.stream()
            .map(assignment -> new AuthenticatedRole(
                assignment.role().name(),
                assignment.institutionId()
            ))
            .sorted(Comparator.comparing(AuthenticatedRole::role)
                .thenComparing(role -> String.valueOf(role.institutionId())))
            .toList();
        return new LocalAuthResult(
            token.value(),
            "Bearer",
            Duration.between(clock.instant(), token.expiresAt()).toSeconds(),
            userId,
            provider,
            roles,
            token.expiresAt()
        );
    }
}
