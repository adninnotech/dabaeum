package com.adn.dabaeum.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class UserAdminUpdateStatusTest {

    private static final UUID USER_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final Instant CREATED_AT = Instant.parse(
        "2026-08-01T00:00:00Z"
    );
    private static final Instant NOW = Instant.parse(
        "2026-08-04T00:00:00Z"
    );

    @Mock
    UserRepository repository;

    private UserApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultUserApplicationService(
            repository,
            () -> USER_ID,
            Clock.fixed(NOW, ZoneOffset.UTC),
            org.mockito.Mockito.mock(com.adn.dabaeum.file.domain.StoredFileRepository.class)
        );
    }

    @ParameterizedTest
    @CsvSource({
        "ACTIVE, DORMANT",
        "ACTIVE, SUSPENDED",
        "ACTIVE, WITHDRAWN",
        "DORMANT, ACTIVE",
        "DORMANT, SUSPENDED",
        "DORMANT, WITHDRAWN",
        "SUSPENDED, ACTIVE",
        "SUSPENDED, DORMANT",
        "SUSPENDED, WITHDRAWN"
    })
    void changesEveryAllowedNonWithdrawnTransition(
        UserStatus current,
        UserStatus target
    ) {
        User existing = user(current, null);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(repository.updateStatus(
            USER_ID,
            target,
            target == UserStatus.WITHDRAWN ? NOW : null,
            NOW
        )).thenReturn(true);

        User changed = service.changeStatus(
            new ChangeUserStatusCommand(USER_ID, target)
        );

        assertThat(changed.id()).isEqualTo(USER_ID);
        assertThat(changed.status()).isEqualTo(target);
        assertThat(changed.withdrawnAt())
            .isEqualTo(target == UserStatus.WITHDRAWN ? NOW : null);
        assertThat(changed.createdAt()).isEqualTo(CREATED_AT);
        assertThat(changed.updatedAt()).isEqualTo(NOW);
        if (target == UserStatus.ACTIVE) {
            verify(repository, never()).invalidateTokens(any(), any());
        } else {
            verify(repository).invalidateTokens(USER_ID, NOW);
        }
        verify(repository).updateStatus(
            USER_ID,
            target,
            target == UserStatus.WITHDRAWN ? NOW : null,
            NOW
        );
    }

    @Test
    void rejectsEveryTransitionFromWithdrawnAsConflict() {
        when(repository.findById(USER_ID)).thenReturn(
            Optional.of(user(UserStatus.WITHDRAWN, NOW))
        );

        for (UserStatus target : UserStatus.values()) {
            ApiException exception = catchThrowableOfType(
                () -> service.changeStatus(
                    new ChangeUserStatusCommand(USER_ID, target)
                ),
                ApiException.class
            );
            assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.code())
                .isEqualTo(ApiErrorCode.USER_STATUS_CONFLICT);
        }

        verify(repository, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void rejectsMissingUserAndNullStatus() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());
        ApiException missing = catchThrowableOfType(
            () -> service.changeStatus(
                new ChangeUserStatusCommand(USER_ID, UserStatus.ACTIVE)
            ),
            ApiException.class
        );
        assertThat(missing.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND);

        ApiException nullStatus = catchThrowableOfType(
            () -> service.changeStatus(
                new ChangeUserStatusCommand(USER_ID, null)
            ),
            ApiException.class
        );
        assertThat(nullStatus.status()).isEqualTo(
            HttpStatus.UNPROCESSABLE_ENTITY
        );
        assertThat(nullStatus.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verify(repository).findById(USER_ID);
    }

    @Test
    void mapsStatusUpdateFalseToNotFound() {
        when(repository.findById(USER_ID)).thenReturn(
            Optional.of(user(UserStatus.ACTIVE, null))
        );
        when(repository.updateStatus(USER_ID, UserStatus.DORMANT, null, NOW))
            .thenReturn(false);

        ApiException exception = catchThrowableOfType(
            () -> service.changeStatus(
                new ChangeUserStatusCommand(USER_ID, UserStatus.DORMANT)
            ),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updatesOnlyPresentProfileFieldsAndPreservesLifecycleFields() {
        User existing = user(UserStatus.SUSPENDED, NOW.minusSeconds(60));
        when(repository.findById(USER_ID)).thenReturn(Optional.of(existing));
        User updated = new User(
            USER_ID,
            "변경 이름",
            existing.email(),
            existing.phone(),
            existing.birthDate(),
            existing.status(),
            existing.withdrawnAt(),
            existing.createdAt(),
            NOW
        );
        when(repository.updateProfile(updated)).thenReturn(true);

        User result = service.updateProfile(new UpdateUserCommand(
            USER_ID,
            UserUpdateField.present(" 변경 이름 "),
            UserUpdateField.absent(),
            UserUpdateField.absent(),
            UserUpdateField.absent()
        ));

        assertThat(result).isEqualTo(updated);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).updateProfile(saved.capture());
        assertThat(saved.getValue().name()).isEqualTo("변경 이름");
        assertThat(saved.getValue().email()).isEqualTo(existing.email());
        assertThat(saved.getValue().status()).isEqualTo(existing.status());
        assertThat(saved.getValue().withdrawnAt())
            .isEqualTo(existing.withdrawnAt());
        assertThat(saved.getValue().createdAt()).isEqualTo(existing.createdAt());
        assertThat(saved.getValue().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void explicitNullClearsNullableProfileFields() {
        User existing = user(UserStatus.ACTIVE, null);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(existing));
        User expected = new User(
            USER_ID,
            existing.name(),
            null,
            null,
            null,
            existing.status(),
            null,
            existing.createdAt(),
            NOW
        );
        when(repository.updateProfile(expected)).thenReturn(true);

        User result = service.updateProfile(new UpdateUserCommand(
            USER_ID,
            UserUpdateField.absent(),
            UserUpdateField.present(null),
            UserUpdateField.present(null),
            UserUpdateField.present(null)
        ));

        assertThat(result).isEqualTo(expected);
        verify(repository).updateProfile(expected);
    }

    @Test
    void rejectsInvalidOrEmptyProfileUpdate() {
        for (UpdateUserCommand command : List.of(
            new UpdateUserCommand(
                USER_ID,
                UserUpdateField.present(null),
                UserUpdateField.absent(),
                UserUpdateField.absent(),
                UserUpdateField.absent()
            ),
            new UpdateUserCommand(
                USER_ID,
                UserUpdateField.present(" "),
                UserUpdateField.absent(),
                UserUpdateField.absent(),
                UserUpdateField.absent()
            ),
            new UpdateUserCommand(
                USER_ID,
                UserUpdateField.absent(),
                UserUpdateField.absent(),
                UserUpdateField.absent(),
                UserUpdateField.absent()
            )
        )) {
            ApiException exception = catchThrowableOfType(
                () -> service.updateProfile(command),
                ApiException.class
            );
            assertThat(exception.status()).isEqualTo(
                HttpStatus.UNPROCESSABLE_ENTITY
            );
            assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void mapsProfileUpdateFalseAndMissingUserToNotFound() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());
        ApiException missing = catchThrowableOfType(
            () -> service.updateProfile(new UpdateUserCommand(
                USER_ID,
                UserUpdateField.present("이름"),
                UserUpdateField.absent(),
                UserUpdateField.absent(),
                UserUpdateField.absent()
            )),
            ApiException.class
        );
        assertThat(missing.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND);

        User existing = user(UserStatus.ACTIVE, null);
        when(repository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(repository.updateProfile(any())).thenReturn(false);
        ApiException falseUpdate = catchThrowableOfType(
            () -> service.updateProfile(new UpdateUserCommand(
                USER_ID,
                UserUpdateField.present("이름"),
                UserUpdateField.absent(),
                UserUpdateField.absent(),
                UserUpdateField.absent()
            )),
            ApiException.class
        );
        assertThat(falseUpdate.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(falseUpdate.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND);
    }

    private User user(UserStatus status, Instant withdrawnAt) {
        return new User(
            USER_ID,
            "기존 사용자",
            "old@example.com",
            "010-0000-0000",
            LocalDate.of(1990, 1, 1),
            status,
            withdrawnAt,
            CREATED_AT,
            NOW.minusSeconds(60)
        );
    }
}
