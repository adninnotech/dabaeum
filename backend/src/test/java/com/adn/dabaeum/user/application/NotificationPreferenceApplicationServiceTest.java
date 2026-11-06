package com.adn.dabaeum.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** 알림 수신 설정은 보낸 값만 바꾸고, 보내지 않은 값은 기존 설정을 그대로 둔다. */
@ExtendWith(MockitoExtension.class)
class NotificationPreferenceApplicationServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "77777777-7777-7777-7777-777777777777"
    );
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");

    @Mock
    UserRepository repository;

    private UserApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultUserApplicationService(
            repository,
            () -> USER_ID,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Mockito.mock(StoredFileRepository.class),
            Mockito.mock(com.adn.dabaeum.role.domain.UserRoleRepository.class),
            org.mockito.Mockito.mock(UserInterestService.class)
        );
    }

    @Test
    void 두_값을_모두_보내면_둘_다_바뀐다() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user(true, false)));
        when(repository.updateNotificationPreferences(USER_ID, false, true, NOW))
            .thenReturn(true);

        User updated = service.updateNotificationPreferences(USER_ID, false, true);

        assertThat(updated.emailNotificationEnabled()).isFalse();
        assertThat(updated.smsNotificationEnabled()).isTrue();
        assertThat(updated.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void 보내지_않은_값은_기존_설정을_유지한다() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user(true, false)));
        when(repository.updateNotificationPreferences(USER_ID, true, true, NOW))
            .thenReturn(true);

        User updated = service.updateNotificationPreferences(USER_ID, null, true);

        assertThat(updated.emailNotificationEnabled()).isTrue();
        assertThat(updated.smsNotificationEnabled()).isTrue();
        verify(repository).updateNotificationPreferences(USER_ID, true, true, NOW);
    }

    @Test
    void 프로필_필드는_건드리지_않는다() {
        User existing = user(true, false);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(repository.updateNotificationPreferences(
            any(), anyBoolean(), anyBoolean(), any())).thenReturn(true);

        User updated = service.updateNotificationPreferences(USER_ID, false, null);

        assertThat(updated.name()).isEqualTo(existing.name());
        assertThat(updated.email()).isEqualTo(existing.email());
        assertThat(updated.status()).isEqualTo(existing.status());
        assertThat(updated.createdAt()).isEqualTo(existing.createdAt());
        verify(repository, never()).updateProfile(any());
    }

    @Test
    void 둘_다_보내지_않으면_422() {
        ApiException exception = catchThrowableOfType(
            () -> service.updateNotificationPreferences(USER_ID, null, null),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verify(repository, never()).updateNotificationPreferences(
            any(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    void 로그인하지_않으면_401() {
        ApiException exception = catchThrowableOfType(
            () -> service.updateNotificationPreferences(null, true, true),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.UNAUTHORIZED);
    }

    @Test
    void 사용자가_없으면_404() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
            () -> service.updateNotificationPreferences(USER_ID, true, true),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND);
    }

    private User user(boolean email, boolean sms) {
        return new User(
            USER_ID,
            "학습자",
            "learner@example.com",
            "010-0000-0000",
            LocalDate.of(1990, 1, 1),
            UserStatus.ACTIVE,
            null,
            CREATED_AT,
            CREATED_AT
        ).withNotificationPreferences(email, sms, CREATED_AT);
    }
}
