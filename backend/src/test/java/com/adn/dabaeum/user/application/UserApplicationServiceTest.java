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
import com.adn.dabaeum.user.domain.UserPageCriteria;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserSort;
import com.adn.dabaeum.user.domain.UserSortDirection;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock
    UserRepository repository;

    @Mock
    UserIdGenerator idGenerator;

    private UserApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultUserApplicationService(
            repository,
            idGenerator,
            Clock.fixed(NOW, ZoneOffset.UTC),
            org.mockito.Mockito.mock(com.adn.dabaeum.file.domain.StoredFileRepository.class)
        );
    }

    @Test
    void createsActiveUserWithTrimmedNameAndClock() {
        when(idGenerator.generate()).thenReturn(USER_ID);

        User created = service.create(new CreateUserCommand(
            " 이름 ",
            "user@example.com",
            "010-1234-5678",
            LocalDate.of(2000, 1, 2),
            null
        ));

        assertThat(created.id()).isEqualTo(USER_ID);
        assertThat(created.name()).isEqualTo("이름");
        assertThat(created.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.updatedAt()).isEqualTo(NOW);
        verify(repository).save(created);
    }

    @Test
    void rejectsBlankName() {
        ApiException exception = catchThrowableOfType(
            () -> service.create(new CreateUserCommand(
                " ", null, null, null, null
            )),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(repository, idGenerator);
    }

    @Test
    void rejectsNullCommand() {
        ApiException exception = catchThrowableOfType(
            () -> service.create(null),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(repository, idGenerator);
    }

    @Test
    void listsUsersWithTypedCriteriaAndTotalPages() {
        List<User> users = List.of(user("첫 사용자"), user("둘 사용자"));
        when(repository.findPage(any())).thenReturn(users);
        when(repository.count(UserStatus.ACTIVE)).thenReturn(21L);

        UserPage page = service.list(new ListUsersQuery(
            2,
            10,
            "name,asc",
            UserStatus.ACTIVE
        ));

        ArgumentCaptor<UserPageCriteria> criteria =
            ArgumentCaptor.forClass(UserPageCriteria.class);
        verify(repository).findPage(criteria.capture());
        verify(repository).count(UserStatus.ACTIVE);
        assertThat(criteria.getValue().offset()).isEqualTo(20);
        assertThat(criteria.getValue().limit()).isEqualTo(10);
        assertThat(criteria.getValue().sort()).isEqualTo(UserSort.NAME);
        assertThat(criteria.getValue().direction()).isEqualTo(UserSortDirection.ASC);
        assertThat(criteria.getValue().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(page.data()).containsExactlyElementsOf(users);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void rejectsMalformedListQueryWithoutRepositoryCall() {
        for (ListUsersQuery query : List.of(
            new ListUsersQuery(-1, 20, "createdAt,desc", null),
            new ListUsersQuery(0, 0, "createdAt,desc", null),
            new ListUsersQuery(0, 101, "createdAt,desc", null),
            new ListUsersQuery(0, 20, "password,desc", null),
            new ListUsersQuery(Integer.MAX_VALUE, 2, "createdAt,desc", null)
        )) {
            ApiException exception = catchThrowableOfType(
                () -> service.list(query),
                ApiException.class
            );
            assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(exception.code()).isEqualTo(ApiErrorCode.BAD_REQUEST);
        }
        verifyNoInteractions(repository);
    }

    @Test
    void returnsUserByIdAndMapsMissingUserToNotFound() {
        User user = user("조회 사용자");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user));
        assertThat(service.get(USER_ID)).isSameAs(user);

        when(repository.findById(USER_ID)).thenReturn(Optional.empty());
        ApiException exception = catchThrowableOfType(
            () -> service.get(USER_ID),
            ApiException.class
        );
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND);
        verify(repository, never()).updateProfile(any());
    }

    private User user(String name) {
        return new User(
            UUID.randomUUID(),
            name,
            "user@example.com",
            "010-0000-0000",
            LocalDate.of(1990, 1, 1),
            UserStatus.ACTIVE,
            null,
            NOW,
            NOW
        );
    }
}
