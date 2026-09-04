package com.adn.dabaeum.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserPageCriteria;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserSort;
import com.adn.dabaeum.user.domain.UserSortDirection;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    UserRepository repository;

    @Test
    void savesAndFindsAllUserFields() {
        User user = user(
            UserStatus.ACTIVE,
            "전체 사용자",
            "user@example.com",
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z"),
            null
        );

        repository.save(user);

        assertThat(repository.findById(user.id())).contains(user);
    }

    @Test
    void listsAllStatusesAndFiltersByStatus() {
        User active = user(UserStatus.ACTIVE, "활성", null, Instant.now(), Instant.now(), null);
        User dormant = user(UserStatus.DORMANT, "휴면", null, Instant.now(), Instant.now(), null);
        User withdrawn = user(
            UserStatus.WITHDRAWN,
            "탈퇴",
            null,
            Instant.now(),
            Instant.now(),
            Instant.parse("2026-08-03T00:00:00Z")
        );
        List.of(active, dormant, withdrawn).forEach(repository::save);

        List<User> all = repository.findPage(new UserPageCriteria(
            0,
            100,
            UserSort.NAME,
            UserSortDirection.ASC,
            null
        ));
        List<User> activeOnly = repository.findPage(new UserPageCriteria(
            0,
            100,
            UserSort.NAME,
            UserSortDirection.ASC,
            UserStatus.ACTIVE
        ));

        assertThat(all).extracting(User::id)
            .contains(active.id(), dormant.id(), withdrawn.id());
        assertThat(activeOnly).extracting(User::id)
            .contains(active.id())
            .doesNotContain(dormant.id(), withdrawn.id());
        assertThat(repository.count(null)).isGreaterThanOrEqualTo(3);
        assertThat(repository.count(UserStatus.ACTIVE)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void appliesCreatedAtDescendingAndOffsetLimit() {
        User older = user(
            UserStatus.ACTIVE,
            "오래된 사용자",
            null,
            Instant.parse("2090-08-01T00:00:00Z"),
            Instant.parse("2090-08-01T00:00:00Z"),
            null
        );
        User newer = user(
            UserStatus.SUSPENDED,
            "새 사용자",
            null,
            Instant.parse("2090-08-02T00:00:00Z"),
            Instant.parse("2090-08-02T00:00:00Z"),
            null
        );
        repository.save(older);
        repository.save(newer);

        List<User> page = repository.findPage(new UserPageCriteria(
            1,
            1,
            UserSort.CREATED_AT,
            UserSortDirection.DESC,
            null
        ));

        assertThat(page).containsExactly(older);
    }

    @Test
    void updatesProfileWithoutChangingLifecycleFields() {
        User original = user(
            UserStatus.ACTIVE,
            "수정 전",
            "before@example.com",
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        );
        repository.save(original);
        User updated = new User(
            original.id(),
            "수정 후",
            null,
            "010-1234-5678",
            LocalDate.of(2000, 1, 2),
            original.status(),
            original.withdrawnAt(),
            original.createdAt(),
            Instant.parse("2026-08-04T00:00:00Z")
        );

        assertThat(repository.updateProfile(updated)).isTrue();
        assertThat(repository.findById(original.id())).contains(updated);
    }

    @Test
    void updatesStatusAndWithdrawnAt() {
        User original = user(
            UserStatus.ACTIVE,
            "상태 변경",
            null,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        );
        repository.save(original);
        Instant withdrawnAt = Instant.parse("2026-08-04T00:00:00Z");

        assertThat(repository.updateStatus(
            original.id(),
            UserStatus.WITHDRAWN,
            withdrawnAt,
            withdrawnAt
        )).isTrue();

        assertThat(repository.findById(original.id()))
            .get()
            .extracting(User::status, User::withdrawnAt)
            .containsExactly(UserStatus.WITHDRAWN, withdrawnAt);
    }

    private User user(
        UserStatus status,
        String name,
        String email,
        Instant createdAt,
        Instant updatedAt,
        Instant withdrawnAt
    ) {
        return new User(
            UUID.randomUUID(),
            name,
            email,
            "010-0000-0000",
            LocalDate.of(1990, 1, 1),
            status,
            withdrawnAt == null ? null : withdrawnAt.truncatedTo(ChronoUnit.MICROS),
            createdAt.truncatedTo(ChronoUnit.MICROS),
            updatedAt.truncatedTo(ChronoUnit.MICROS)
        );
    }
}
