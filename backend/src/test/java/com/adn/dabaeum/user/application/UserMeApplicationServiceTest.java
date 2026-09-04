package com.adn.dabaeum.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class UserMeApplicationServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "55555555-5555-5555-5555-555555555555"
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

    @Test
    void getsOnlyThePrincipalUser() {
        User existing = user();
        when(repository.findById(USER_ID)).thenReturn(Optional.of(existing));

        assertThat(service.getMe(USER_ID)).isSameAs(existing);
        verify(repository).findById(USER_ID);
    }

    @Test
    void mapsMissingPrincipalUserToNotFound() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        ApiException exception = catchThrowableOfType(
            () -> service.getMe(USER_ID),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updatesOnlyProfileFieldsAndPreservesStatusLifecycle() {
        User existing = user();
        when(repository.findById(USER_ID)).thenReturn(Optional.of(existing));
        User expected = new User(
            USER_ID,
            "본인 변경",
            null,
            existing.phone(),
            existing.birthDate(),
            existing.status(),
            existing.withdrawnAt(),
            existing.createdAt(),
            NOW
        );
        when(repository.updateProfile(expected)).thenReturn(true);

        User result = service.updateMe(USER_ID, new UpdateMeCommand(
            UserUpdateField.present(" 본인 변경 "),
            UserUpdateField.present(null),
            UserUpdateField.absent(),
            UserUpdateField.absent()
        ));

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).updateProfile(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(USER_ID);
        assertThat(saved.getValue().status()).isEqualTo(existing.status());
        assertThat(saved.getValue().withdrawnAt())
            .isEqualTo(existing.withdrawnAt());
        assertThat(saved.getValue().createdAt()).isEqualTo(CREATED_AT);
        assertThat(saved.getValue().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsEmptySelfUpdate() {
        ApiException exception = catchThrowableOfType(
            () -> service.updateMe(USER_ID, new UpdateMeCommand(
                UserUpdateField.absent(),
                UserUpdateField.absent(),
                UserUpdateField.absent(),
                UserUpdateField.absent()
            )),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(
            HttpStatus.UNPROCESSABLE_ENTITY
        );
        assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    private User user() {
        return new User(
            USER_ID,
            "본인 사용자",
            "me@example.com",
            "010-0000-0000",
            LocalDate.of(1990, 1, 1),
            UserStatus.ACTIVE,
            null,
            CREATED_AT,
            CREATED_AT
        );
    }
}
