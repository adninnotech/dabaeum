package com.adn.dabaeum.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authentication.domain.LocalAccountCredential;
import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import com.adn.dabaeum.authentication.domain.LoginAttemptRepository;
import com.adn.dabaeum.authentication.domain.PasswordAge;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.identity.application.IdentityIdGenerator;
import com.adn.dabaeum.role.application.RoleIdGenerator;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.application.UserIdGenerator;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserAgreementRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 설계서 §10.3 — 자체 로그인 5회 연속 실패 시 30분 잠금. */
@ExtendWith(MockitoExtension.class)
class LoginLockoutTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final String EMAIL = "admin@example.com";
    private static final String PASSWORD = "correct-password-1";

    @Mock LocalAccountRepository localAccountRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock UserAgreementRepository userAgreementRepository;
    @Mock UserIdGenerator userIdGenerator;
    @Mock IdentityIdGenerator identityIdGenerator;
    @Mock RoleIdGenerator roleIdGenerator;
    @Mock LocalAccessTokenService tokenService;
    @Mock LoginAttemptRepository loginAttempts;

    private PasswordEncoder passwordEncoder;
    private DefaultLocalAccountApplicationService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        service = new DefaultLocalAccountApplicationService(
            localAccountRepository, userRepository, userRoleRepository, userAgreementRepository,
            userIdGenerator, identityIdGenerator, roleIdGenerator, passwordEncoder, tokenService,
            loginAttempts, Clock.fixed(NOW, ZoneOffset.UTC));
        when(localAccountRepository.findByNormalizedEmail(EMAIL)).thenReturn(Optional.of(new LocalAccountCredential(
            UUID.randomUUID(), USER_ID, EMAIL, passwordEncoder.encode(PASSWORD), NOW, NOW)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User(
            USER_ID, "관리자", EMAIL, null, null, UserStatus.ACTIVE, null, NOW, NOW)));
    }

    @Test
    void wrongPasswordCountsOneFailureAndAnswersAuthenticationFailed() {
        when(loginAttempts.recordFailure(USER_ID, 5, NOW.plusSeconds(1_800))).thenReturn(false);

        assertCode(() -> service.login(new LoginCommand(EMAIL, "wrong-password-1")), ApiErrorCode.AUTHENTICATION_FAILED);
        verify(userRepository, never()).recordLastLogin(any(), any());
    }

    @Test
    void failureThatReachesTheThresholdAnswersAccountLocked() {
        when(loginAttempts.recordFailure(USER_ID, 5, NOW.plusSeconds(1_800))).thenReturn(true);

        assertCode(() -> service.login(new LoginCommand(EMAIL, "wrong-password-1")), ApiErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void lockedAccountIsRejectedEvenWithTheRightPassword() {
        when(loginAttempts.findLockedUntil(USER_ID)).thenReturn(Optional.of(NOW.plusSeconds(60)));

        assertCode(() -> service.login(new LoginCommand(EMAIL, PASSWORD)), ApiErrorCode.ACCOUNT_LOCKED);
        verify(loginAttempts, never()).recordFailure(any(), anyInt(), any());
        verify(tokenService, never()).issue(any());
    }

    @Test
    void expiredLockDoesNotBlockAndSuccessClearsFailures() {
        when(loginAttempts.findLockedUntil(USER_ID)).thenReturn(Optional.of(NOW.minusSeconds(1)));
        when(tokenService.issue(USER_ID)).thenReturn(new IssuedAccessToken("token", NOW.plusSeconds(3_600)));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of());

        service.login(new LoginCommand(EMAIL, PASSWORD));

        verify(loginAttempts).clear(USER_ID);
        verify(userRepository).recordLastLogin(USER_ID, NOW);
    }

    @Test
    void adminLoginReportsExpiredPassword() {
        when(tokenService.issue(USER_ID)).thenReturn(new IssuedAccessToken("token", NOW.plusSeconds(3_600)));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(new UserRoleAssignment(
            UUID.randomUUID(), USER_ID, null, UserRole.PLATFORM_ADMIN, NOW)));
        when(localAccountRepository.findPasswordAge(USER_ID)).thenReturn(Optional.of(new PasswordAge(null)));

        assertThat(service.login(new LoginCommand(EMAIL, PASSWORD)).passwordExpired()).isTrue();
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ApiErrorCode code) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.code()).isEqualTo(code);
                assertThat(exception.status().value()).isEqualTo(401);
            });
    }
}
