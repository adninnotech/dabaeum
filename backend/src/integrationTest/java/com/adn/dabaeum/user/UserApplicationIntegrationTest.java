package com.adn.dabaeum.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.application.ChangeUserStatusCommand;
import com.adn.dabaeum.user.application.CreateUserCommand;
import com.adn.dabaeum.user.application.ListUsersQuery;
import com.adn.dabaeum.user.application.UpdateUserCommand;
import com.adn.dabaeum.user.application.UserApplicationService;
import com.adn.dabaeum.user.application.UserPage;
import com.adn.dabaeum.user.application.UserUpdateField;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserApplicationIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    UserApplicationService service;

    @Autowired
    UserRepository repository;

    @Test
    void savesAndFindsAllUserFieldsThroughApplicationService() {
        User created = service.create(new CreateUserCommand(
            "Task6 전체 필드 " + UUID.randomUUID(),
            "task6-fields@example.com",
            "010-1234-5678",
            LocalDate.of(1995, 6, 7),
            UserStatus.SUSPENDED
        ));

        assertThat(repository.findById(created.id())).contains(created);
    }

    @Test
    void listsStatusesFiltersSortsAndPagesThroughApplicationService() {
        Map<UserStatus, Long> beforeByStatus = new EnumMap<>(UserStatus.class);
        for (UserStatus status : UserStatus.values()) {
            beforeByStatus.put(status, repository.count(status));
        }
        long beforeAll = repository.count(null);

        List<User> created = List.of(
            create("Task6-A-" + UUID.randomUUID(), UserStatus.ACTIVE),
            create("Task6-B-" + UUID.randomUUID(), UserStatus.DORMANT),
            create("Task6-C-" + UUID.randomUUID(), UserStatus.SUSPENDED),
            create("Task6-D-" + UUID.randomUUID(), UserStatus.WITHDRAWN)
        );

        UserPage all = service.list(new ListUsersQuery(
            0,
            100,
            "name,asc",
            null
        ));
        assertThat(all.totalElements()).isEqualTo(beforeAll + 4);
        assertThat(all.data())
            .isSortedAccordingTo(Comparator.comparing(User::name))
            .extracting(User::id)
            .containsAll(created.stream().map(User::id).toList());

        for (UserStatus status : UserStatus.values()) {
            User expected = created.stream()
                .filter(user -> user.status() == status)
                .findFirst()
                .orElseThrow();
            UserPage filtered = service.list(new ListUsersQuery(
                0,
                100,
                "status,asc",
                status
            ));
            assertThat(filtered.totalElements())
                .isEqualTo(beforeByStatus.get(status) + 1);
            assertThat(filtered.data())
                .extracting(User::id)
                .contains(expected.id());
        }

        UserPage firstActive = service.list(new ListUsersQuery(
            0,
            1,
            "name,asc",
            UserStatus.ACTIVE
        ));
        assertThat(firstActive.size()).isEqualTo(1);
        assertThat(firstActive.totalElements())
            .isEqualTo(beforeByStatus.get(UserStatus.ACTIVE) + 1);
        assertThat(firstActive.totalPages())
            .isEqualTo(beforeByStatus.get(UserStatus.ACTIVE).intValue() + 1);
    }

    @Test
    void updatesProfileNullableFieldsAndPreservesLifecycleFields() {
        User original = create(
            "Task6 수정 전 " + UUID.randomUUID(),
            UserStatus.DORMANT
        );

        User updated = service.updateProfile(new UpdateUserCommand(
            original.id(),
            UserUpdateField.present("Task6 수정 후"),
            UserUpdateField.present(null),
            UserUpdateField.present("010-9999-8888"),
            UserUpdateField.present(LocalDate.of(2001, 2, 3))
        ));
        User persisted = repository.findById(original.id()).orElseThrow();

        assertThat(updated.name()).isEqualTo("Task6 수정 후");
        assertThat(persisted.name()).isEqualTo("Task6 수정 후");
        assertThat(persisted.email()).isNull();
        assertThat(persisted.phone()).isEqualTo("010-9999-8888");
        assertThat(persisted.birthDate()).isEqualTo(LocalDate.of(2001, 2, 3));
        assertThat(persisted.status()).isEqualTo(original.status());
        assertThat(persisted.withdrawnAt()).isEqualTo(original.withdrawnAt());
        assertThat(persisted.createdAt()).isEqualTo(original.createdAt());
        assertThat(persisted.updatedAt()).isAfterOrEqualTo(original.updatedAt());
    }

    @Test
    void withdrawsUserRecordsWithdrawnAtAndRejectsReactivation() {
        User original = create(
            "Task6 탈퇴 " + UUID.randomUUID(),
            UserStatus.ACTIVE
        );

        User withdrawn = service.changeStatus(new ChangeUserStatusCommand(
            original.id(),
            UserStatus.WITHDRAWN
        ));
        User persisted = repository.findById(original.id()).orElseThrow();

        assertThat(withdrawn.status()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(withdrawn.withdrawnAt()).isNotNull();
        assertThat(persisted.status()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(persisted.withdrawnAt()).isNotNull();

        ApiException conflict = catchThrowableOfType(
            () -> service.changeStatus(new ChangeUserStatusCommand(
                original.id(),
                UserStatus.ACTIVE
            )),
            ApiException.class
        );
        assertThat(conflict.code()).isEqualTo(ApiErrorCode.USER_STATUS_CONFLICT);
    }

    @Test
    void mapsMissingUserToUserNotFound() {
        UUID missing = UUID.randomUUID();

        ApiException getException = catchThrowableOfType(
            () -> service.get(missing),
            ApiException.class
        );
        ApiException meException = catchThrowableOfType(
            () -> service.getMe(missing),
            ApiException.class
        );

        assertThat(getException.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND);
        assertThat(meException.code()).isEqualTo(ApiErrorCode.USER_NOT_FOUND);
    }

    private User create(String name, UserStatus status) {
        return service.create(new CreateUserCommand(
            name,
            name.toLowerCase().replace(' ', '-') + "@example.com",
            "010-0000-0000",
            LocalDate.of(1990, 1, 1),
            status
        ));
    }
}
