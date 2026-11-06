package com.adn.dabaeum.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import com.adn.dabaeum.authentication.domain.PasswordAge;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 설계서 §10.3 — 관리자 역할 + LOCAL 비밀번호가 90일 이상 그대로면 안내한다. 차단하지 않는다. */
@ExtendWith(MockitoExtension.class)
class PasswordExpiryPolicyTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Mock LocalAccountRepository accounts;
    private PasswordExpiryPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new PasswordExpiryPolicy(accounts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void learnerIsNeverExpiredAndIsNotLookedUp() {
        assertThat(policy.isExpired(USER_ID, List.of("LEARNER", "INSTRUCTOR"))).isFalse();
        verifyNoInteractions(accounts);
    }

    @Test
    void adminPasswordOlderThanNinetyDaysIsExpired() {
        when(accounts.findPasswordAge(USER_ID)).thenReturn(Optional.of(new PasswordAge(NOW.minus(Duration.ofDays(90)))));

        assertThat(policy.isExpired(USER_ID, List.of("PLATFORM_ADMIN"))).isTrue();
    }

    @Test
    void adminPasswordChangedRecentlyIsNotExpired() {
        when(accounts.findPasswordAge(USER_ID)).thenReturn(Optional.of(new PasswordAge(NOW.minus(Duration.ofDays(89)))));

        assertThat(policy.isExpired(USER_ID, List.of("INSTITUTION_ADMIN"))).isFalse();
    }

    @Test
    void temporaryPasswordIsExpired() {
        when(accounts.findPasswordAge(USER_ID)).thenReturn(Optional.of(new PasswordAge(null)));

        assertThat(policy.isExpired(USER_ID, List.of("PLATFORM_ADMIN"))).isTrue();
    }

    @Test
    void adminWithoutLocalPasswordIsNotExpired() {
        when(accounts.findPasswordAge(USER_ID)).thenReturn(Optional.empty());

        assertThat(policy.isExpired(USER_ID, List.of("PLATFORM_ADMIN"))).isFalse();
    }
}
