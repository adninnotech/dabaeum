package com.adn.dabaeum.authentication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.authentication.domain.LocalAccountCredential;
import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import com.adn.dabaeum.authentication.domain.LoginAttemptRepository;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.identity.application.IdentityIdGenerator;
import com.adn.dabaeum.role.application.RoleIdGenerator;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.application.UserIdGenerator;
import com.adn.dabaeum.user.domain.UserAgreementRepository;
import com.adn.dabaeum.user.domain.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 로그인한 본인이 자기 비밀번호를 바꾸는 경로.
 * 다대구 DID로만 가입한 계정은 로컬 자격증명이 없어 404여야 프론트가 버튼을 숨길 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class ChangePasswordTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID IDENTITY_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

    @Mock LocalAccountRepository localAccountRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock UserAgreementRepository userAgreementRepository;
    @Mock UserIdGenerator userIdGenerator;
    @Mock IdentityIdGenerator identityIdGenerator;
    @Mock RoleIdGenerator roleIdGenerator;
    @Mock LocalAccessTokenService tokenService;
    @Mock LoginAttemptRepository loginAttempts;
    @Mock PasswordEncoder passwordEncoder;

    private LocalAccountApplicationService service;
    private LocalAccountCredential credential;

    @BeforeEach
    void setUp() {
        service = new DefaultLocalAccountApplicationService(
            localAccountRepository,
            userRepository,
            userRoleRepository,
            userAgreementRepository,
            userIdGenerator,
            identityIdGenerator,
            roleIdGenerator,
            passwordEncoder,
            tokenService,
            loginAttempts,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        credential = new LocalAccountCredential(
            IDENTITY_ID, USER_ID, "learner@example.com", "old-hash", NOW, NOW
        );
    }

    @Test
    void 현재_비밀번호가_맞으면_바꾸고_토큰을_무효화한다() {
        when(localAccountRepository.findByUserId(USER_ID))
            .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("current-pass1", credential.passwordHash()))
            .thenReturn(true);
        when(passwordEncoder.encode("new-password1")).thenReturn("new-hash");
        when(localAccountRepository.updatePasswordHash(USER_ID, "new-hash", NOW))
            .thenReturn(true);

        service.changePassword(new ChangePasswordCommand(
            USER_ID, "current-pass1", "new-password1"));

        verify(localAccountRepository)
            .updatePasswordHash(eq(USER_ID), eq("new-hash"), eq(NOW));
        verify(userRepository).invalidateTokens(USER_ID, NOW);
    }

    @Test
    void 현재_비밀번호가_틀리면_401() {
        when(localAccountRepository.findByUserId(USER_ID))
            .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("wrong-pass1", credential.passwordHash()))
            .thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordCommand(
            USER_ID, "wrong-pass1", "new-password1")))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).status())
                .isEqualTo(HttpStatus.UNAUTHORIZED));

        verify(localAccountRepository, never()).updatePasswordHash(any(), any(), any());
        verify(userRepository, never()).invalidateTokens(any(), any());
    }

    @Test
    void 새_비밀번호가_10자_미만이면_422() {
        assertThatThrownBy(() -> service.changePassword(new ChangePasswordCommand(
            USER_ID, "current-pass1", "short")))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).details())
                .containsExactly("newPassword"));

        verify(localAccountRepository, never()).updatePasswordHash(any(), any(), any());
        verify(userRepository, never()).invalidateTokens(any(), any());
    }

    @Test
    void 현재와_같은_비밀번호로는_바꿀_수_없다() {
        when(localAccountRepository.findByUserId(USER_ID))
            .thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("current-pass1", credential.passwordHash()))
            .thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordCommand(
            USER_ID, "current-pass1", "current-pass1")))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).details())
                .containsExactly("newPassword"));

        verify(localAccountRepository, never()).updatePasswordHash(any(), any(), any());
        verify(userRepository, never()).invalidateTokens(any(), any());
    }

    @Test
    void 로컬_계정이_없으면_404() {
        // 다대구 DID 로만 가입한 사용자
        when(localAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordCommand(
            USER_ID, "current-pass1", "new-password1")))
            .isInstanceOf(ApiException.class)
            .satisfies(e -> assertThat(((ApiException) e).status())
                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(userRepository, never()).invalidateTokens(any(), any());
    }
}
