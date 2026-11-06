package com.adn.dabaeum.authentication.application;

import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 설계서 §10.3 — 관리자 비밀번호 90일. 안내만 하고 막지 않는다. */
@Component
@Profile({"local", "dev"})
public class PasswordExpiryPolicy {

    static final Duration MAX_AGE = Duration.ofDays(90);
    private static final Set<String> ADMIN_ROLES = Set.of("PLATFORM_ADMIN", "INSTITUTION_ADMIN");

    private final LocalAccountRepository accounts;
    private final Clock clock;

    public PasswordExpiryPolicy(LocalAccountRepository accounts, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean isExpired(UUID userId, Collection<String> roles) {
        if (roles.stream().noneMatch(ADMIN_ROLES::contains)) {
            return false;
        }
        Instant now = clock.instant();
        return accounts.findPasswordAge(userId)
            .map(age -> age.changedAt() == null || !age.changedAt().plus(MAX_AGE).isAfter(now))
            .orElse(false);
    }
}
