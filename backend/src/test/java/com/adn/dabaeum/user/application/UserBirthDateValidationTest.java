package com.adn.dabaeum.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.file.domain.StoredFileRepository;
import com.adn.dabaeum.role.domain.UserRoleRepository;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** 생년월일은 서울 기준 오늘까지만 받는다. 회원가입의 @PastOrPresent 와 같은 기준이다. */
@ExtendWith(MockitoExtension.class)
class UserBirthDateValidationTest {

    private static final UUID USER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    /** UTC 로는 10월 19일이지만 서울은 이미 10월 20일 00:30 이다. */
    private static final Instant NOW = Instant.parse("2026-10-19T15:30:00Z");
    private static final LocalDate SEOUL_TODAY = LocalDate.of(2026, 10, 20);

    @Mock UserRepository repository;

    private UserApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultUserApplicationService(
            repository, () -> USER_ID, Clock.fixed(NOW, ZoneOffset.UTC),
            Mockito.mock(StoredFileRepository.class), Mockito.mock(UserRoleRepository.class),
            org.mockito.Mockito.mock(UserInterestService.class)
        );
    }

    @Test
    void updateMeRejectsTomorrowBeforeLoadingTheUser() {
        ApiException exception = catchThrowableOfType(
            () -> service.updateMe(USER_ID, onlyBirthDate(SEOUL_TODAY.plusDays(1))), ApiException.class);

        assertRejected(exception);
        verify(repository, never()).findById(any());
        verify(repository, never()).updateProfile(any());
    }

    @Test
    void updateMeAcceptsSeoulTodayAndClearing() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(stored()));
        when(repository.updateProfile(any())).thenReturn(true);

        service.updateMe(USER_ID, onlyBirthDate(SEOUL_TODAY));
        service.updateMe(USER_ID, onlyBirthDate(null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository, Mockito.times(2)).updateProfile(saved.capture());
        assertThat(saved.getAllValues()).extracting(User::birthDate).containsExactly(SEOUL_TODAY, null);
    }

    @Test
    void adminUpdateRejectsFutureBirthDate() {
        UpdateUserCommand command = new UpdateUserCommand(USER_ID,
            UserUpdateField.absent(), UserUpdateField.absent(), UserUpdateField.absent(),
            UserUpdateField.present(SEOUL_TODAY.plusDays(1)));

        assertRejected(catchThrowableOfType(() -> service.updateProfile(command), ApiException.class));
        verify(repository, never()).updateProfile(any());
    }

    @Test
    void adminCreateRejectsFutureBirthDate() {
        CreateUserCommand command = new CreateUserCommand("홍길동", null, null, SEOUL_TODAY.plusDays(1), null);

        assertRejected(catchThrowableOfType(() -> service.create(command), ApiException.class));
        verify(repository, never()).save(any());
    }

    private static void assertRejected(ApiException exception) {
        assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(exception.details()).containsExactly("birthDate");
    }

    private static UpdateMeCommand onlyBirthDate(LocalDate birthDate) {
        return new UpdateMeCommand(
            UserUpdateField.absent(), UserUpdateField.absent(), UserUpdateField.absent(),
            UserUpdateField.present(birthDate), UserUpdateField.absent(), UserUpdateField.absent(),
            UserUpdateField.absent(), UserUpdateField.absent());
    }

    private static User stored() {
        return new User(USER_ID, "홍길동", null, null, LocalDate.of(1990, 1, 15),
            UserStatus.ACTIVE, null, NOW, NOW,
            null, null, null, null, null, true, false, null);
    }
}
