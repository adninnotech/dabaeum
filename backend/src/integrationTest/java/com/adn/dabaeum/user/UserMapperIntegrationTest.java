package com.adn.dabaeum.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.api.KeywordQuery;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.Gender;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserListFilter;
import com.adn.dabaeum.user.domain.UserListItem;
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

        User found = repository.findById(user.id()).orElseThrow();
        assertThat(found).usingRecursiveComparison().isEqualTo(user);
    }

    @Test
    void listsAllStatusesAndFiltersByStatus() {
        // 개발 DB 에는 사용자가 수천 명이라 앞 100명에 방금 만든 사용자가 든다는 보장이 없다.
        // 이름에 태그를 넣고 그 태그로 검색해 이 테스트의 사용자만 본다.
        String tag = "상태목록" + UUID.randomUUID();
        String keyword = KeywordQuery.normalize(tag);
        User active = user(UserStatus.ACTIVE, tag + "활성", null, Instant.now(), Instant.now(), null);
        User dormant = user(UserStatus.DORMANT, tag + "휴면", null, Instant.now(), Instant.now(), null);
        User withdrawn = user(
            UserStatus.WITHDRAWN,
            tag + "탈퇴",
            null,
            Instant.now(),
            Instant.now(),
            Instant.parse("2026-08-03T00:00:00Z")
        );
        List.of(active, dormant, withdrawn).forEach(repository::save);

        List<UserListItem> all = repository.findPage(new UserPageCriteria(
            0,
            100,
            UserSort.NAME,
            UserSortDirection.ASC, UserListFilter.basic(null, keyword, null)));
        List<UserListItem> activeOnly = repository.findPage(new UserPageCriteria(
            0,
            100,
            UserSort.NAME,
            UserSortDirection.ASC, UserListFilter.basic(UserStatus.ACTIVE, keyword, null)));

        assertThat(all).extracting(item -> item.user().id())
            .containsExactlyInAnyOrder(active.id(), dormant.id(), withdrawn.id());
        assertThat(activeOnly).extracting(item -> item.user().id())
            .containsExactly(active.id());
        // 수강·강사 배정이 전혀 없는 사용자는 목록에서 0 이다(null 아님).
        assertThat(all).filteredOn(item -> item.user().id().equals(active.id()))
            .extracting(UserListItem::enrollmentCount, UserListItem::courseCount)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(0L, 0L));
        assertThat(repository.count(UserListFilter.basic(null, keyword, null))).isEqualTo(3);
        assertThat(repository.count(UserListFilter.basic(UserStatus.ACTIVE, keyword, null))).isEqualTo(1);
    }

    @Test
    void keywordSearchIgnoresPhoneHyphensAndFiltersByRole() {
        // 화면에서 하이픈을 빼고 검색해도 "010-1234-5678" 로 저장된 연락처가 걸려야 한다.
        String email = uniqueCode("keyword") + "@example.com";
        User target = new User(
            UUID.randomUUID(),
            "검색전용" + UUID.randomUUID(),
            email,
            "010-1234-5678",
            LocalDate.of(1990, 1, 1),
            UserStatus.ACTIVE,
            null,
            Instant.now().truncatedTo(ChronoUnit.MICROS),
            Instant.now().truncatedTo(ChronoUnit.MICROS)
        );
        repository.save(target);

        String phoneKeyword = KeywordQuery.normalize("01012345678");
        List<UserListItem> byPhone = repository.findPage(new UserPageCriteria(
            0, 100, UserSort.NAME, UserSortDirection.ASC, UserListFilter.basic(null, phoneKeyword, null)));
        assertThat(byPhone).extracting(item -> item.user().id()).contains(target.id());

        String emailKeyword = KeywordQuery.normalize(email);
        List<UserListItem> byEmail = repository.findPage(new UserPageCriteria(
            0, 100, UserSort.NAME, UserSortDirection.ASC, UserListFilter.basic(null, emailKeyword, null)));
        assertThat(byEmail).extracting(item -> item.user().id()).containsExactly(target.id());
        assertThat(repository.count(UserListFilter.basic(null, emailKeyword, null))).isEqualTo(1);

        // role 필터는 tb_user_roles 에 INSTRUCTOR 행이 있는 사용자만 걸러야 한다.
        jdbcTemplate.update("""
            INSERT INTO tb_user_roles (id, user_id, institution_id, role, created_at)
            VALUES (?, ?, NULL, 'INSTRUCTOR', ?)
            """, UUID.randomUUID(), target.id(), java.time.OffsetDateTime.now());
        // 강좌에 강사로 배정하면 courseCount 가 걸린 강좌 수만큼 올라야 한다.
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("ic"), uniqueCode("in"));
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, courseId, institutionId, uniqueCode("cc"), uniqueCode("ct"),
            "OFFLINE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 20);
        jdbcTemplate.update("""
            INSERT INTO tb_course_instructors (id, course_id, user_id, instructor_role)
            VALUES (?, ?, ?, 'MAIN')
            """, UUID.randomUUID(), courseId, target.id());

        List<UserListItem> byRole = repository.findPage(new UserPageCriteria(
            0, 100, UserSort.NAME, UserSortDirection.ASC, UserListFilter.basic(null, emailKeyword, UserRole.INSTRUCTOR)));
        List<UserListItem> byOtherRole = repository.findPage(new UserPageCriteria(
            0, 100, UserSort.NAME, UserSortDirection.ASC, UserListFilter.basic(null, emailKeyword, UserRole.LEARNER)));
        assertThat(byRole).extracting(item -> item.user().id()).contains(target.id());
        assertThat(byOtherRole).extracting(item -> item.user().id()).doesNotContain(target.id());
        assertThat(byRole)
            .filteredOn(item -> item.user().id().equals(target.id()))
            .extracting(UserListItem::courseCount)
            .containsExactly(1L);
    }

    @Test
    void filtersStudentListByGenderEnrollmentLastLoginAndCreatedAt() {
        // 같은 태그를 이름에 넣고 그 태그로 검색해, 개발 DB 의 다른 사용자와 섞이지 않게 한다.
        String tag = "학생필터" + UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        // 서울 날짜 경계: 2026-10-10T15:00Z 가 10-11 00:00 KST 다.
        User active = user(UserStatus.ACTIVE, tag + "A", null,
            Instant.parse("2026-10-10T15:30:00Z"), now, null);
        User stale = user(UserStatus.ACTIVE, tag + "B", null,
            Instant.parse("2026-10-10T14:30:00Z"), now, null);
        User never = user(UserStatus.ACTIVE, tag + "C", null,
            Instant.parse("2026-10-12T00:00:00Z"), now, null);
        List.of(active, stale, never).forEach(repository::save);
        setStudentFields(active.id(), "FEMALE", now.minus(3, ChronoUnit.DAYS));
        setStudentFields(stale.id(), null, now.minus(100, ChronoUnit.DAYS));
        setStudentFields(never.id(), "UNDISCLOSED", null);
        approveEnrollment(active.id());

        String keyword = KeywordQuery.normalize(tag);
        assertThat(ids(filter(keyword, Gender.UNDISCLOSED, null, null, null, false, null, null)))
            .containsExactly(never.id());
        assertThat(ids(filter(keyword, null, true, null, null, false, null, null)))
            .containsExactly(active.id());
        assertThat(ids(filter(keyword, null, false, null, null, false, null, null)))
            .containsExactlyInAnyOrder(stale.id(), never.id());
        assertThat(ids(filter(keyword, null, null,
            now.minus(7, ChronoUnit.DAYS), null, false, null, null)))
            .containsExactly(active.id());
        // OVER_90 은 기록 없는 사람을 빼고, NEVER 는 기록 없는 사람만 남긴다.
        assertThat(ids(filter(keyword, null, null,
            null, now.minus(90, ChronoUnit.DAYS), false, null, null)))
            .containsExactly(stale.id());
        assertThat(ids(filter(keyword, null, null, null, null, true, null, null)))
            .containsExactly(never.id());
        // 가입일 2026-10-11(서울) 하루: [10-10T15:00Z, 10-11T15:00Z)
        UserListFilter oneDay = filter(keyword, null, null, null, null, false,
            Instant.parse("2026-10-10T15:00:00Z"), Instant.parse("2026-10-11T15:00:00Z"));
        assertThat(ids(oneDay)).containsExactly(active.id());
        assertThat(repository.count(oneDay)).isEqualTo(1);
    }

    @Test
    void sortsByEnrollmentCountAndKeepsNeverLoggedInLastForLastLoginSort() {
        String tag = "학생정렬" + UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        User recent = user(UserStatus.ACTIVE, tag + "A", null, now, now, null);
        User old = user(UserStatus.ACTIVE, tag + "B", null, now, now, null);
        User never = user(UserStatus.ACTIVE, tag + "C", null, now, now, null);
        List.of(recent, old, never).forEach(repository::save);
        setStudentFields(recent.id(), null, now.minus(1, ChronoUnit.DAYS));
        setStudentFields(old.id(), null, now.minus(10, ChronoUnit.DAYS));
        approveEnrollment(old.id());
        approveEnrollment(old.id());
        approveEnrollment(never.id());
        UserListFilter byTag = filter(KeywordQuery.normalize(tag),
            null, null, null, null, false, null, null);

        for (UserSortDirection direction : UserSortDirection.values()) {
            List<UUID> byLogin = repository.findPage(new UserPageCriteria(
                0, 10, UserSort.LAST_LOGIN_AT, direction, byTag))
                .stream().map(item -> item.user().id()).toList();
            assertThat(byLogin).last().isEqualTo(never.id());
        }
        List<UserListItem> byEnrollment = repository.findPage(new UserPageCriteria(
            0, 10, UserSort.ENROLLMENT_COUNT, UserSortDirection.DESC, byTag));
        assertThat(byEnrollment).extracting(item -> item.user().id())
            .containsExactly(old.id(), never.id(), recent.id());
        assertThat(byEnrollment).extracting(UserListItem::enrollmentCount)
            .containsExactly(2L, 1L, 0L);
    }

    private UserListFilter filter(
        String keyword, Gender gender, Boolean hasEnrollment,
        Instant lastLoginFrom, Instant lastLoginBefore, boolean lastLoginNever,
        Instant createdFrom, Instant createdBefore
    ) {
        return new UserListFilter(null, keyword, null, gender, hasEnrollment,
            lastLoginFrom, lastLoginBefore, lastLoginNever, createdFrom, createdBefore);
    }

    private List<UUID> ids(UserListFilter filter) {
        return repository.findPage(new UserPageCriteria(
                0, 100, UserSort.CREATED_AT, UserSortDirection.DESC, filter))
            .stream().map(item -> item.user().id()).toList();
    }

    private void setStudentFields(UUID userId, String gender, Instant lastLoginAt) {
        jdbcTemplate.update(
            "UPDATE tb_users SET gender = ?, last_login_at = ? WHERE id = ?",
            gender,
            lastLoginAt == null ? null : java.sql.Timestamp.from(lastLoginAt), userId);
    }

    private void approveEnrollment(UUID userId) {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("ic"), uniqueCode("in"));
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, courseId, institutionId, uniqueCode("cc"), uniqueCode("ct"),
            "OFFLINE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 20);
        jdbcTemplate.update("""
            INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at)
            VALUES (?, ?, ?, 'APPROVED', ?)
            """, UUID.randomUUID(), courseId, userId, java.time.OffsetDateTime.now());
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

        List<UserListItem> page = repository.findPage(new UserPageCriteria(
            1,
            1,
            UserSort.CREATED_AT,
            UserSortDirection.DESC, UserListFilter.basic(null, null, null)));

        assertThat(page).extracting(UserListItem::user)
            .usingRecursiveFieldByFieldElementComparator()
            .containsExactly(older);
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
        assertThat(repository.findById(original.id())).get()
            .usingRecursiveComparison()
            .isEqualTo(updated);
    }

    @Test
    void savesAndReadsBackUndisclosedGender() {
        // UNDISCLOSED 는 11자 — V27 의 VARCHAR(10) 에서는 저장이 거부됐다(V28 이 20으로 넓힘).
        User original = user(
            UserStatus.ACTIVE,
            "비공개 성별",
            null,
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-01T00:00:00Z"),
            null
        ).withGender(Gender.UNDISCLOSED);

        repository.save(original);

        assertThat(repository.findById(original.id()))
            .get()
            .extracting(User::gender)
            .isEqualTo(Gender.UNDISCLOSED);
    }

    @Test
    void keepsNotificationPreferencesWhenProfileIsUpdatedLater() {
        // 공유 dev DB 라 전체 개수·절대값은 단언하지 않는다. 이 테스트가 만든 사용자만 본다.
        User original = user(
            UserStatus.ACTIVE,
            "알림 설정",
            null,
            Instant.parse("2026-09-17T00:00:00Z"),
            Instant.parse("2026-09-17T00:00:00Z"),
            null
        );
        repository.save(original);
        Instant changedAt = Instant.parse("2026-09-18T00:00:00Z")
            .truncatedTo(ChronoUnit.MICROS);

        assertThat(repository.updateNotificationPreferences(
            original.id(), false, true, changedAt)).isTrue();
        assertThat(repository.findById(original.id()))
            .get()
            .extracting(User::emailNotificationEnabled, User::smsNotificationEnabled)
            .containsExactly(false, true);

        // original 은 기본값(이메일 true, SMS false)을 들고 있다 — DB 에 저장된 (false, true) 와
        // 일부러 어긋나게 둔다. updateProfile 의 SET 절에 알림 컬럼이 섞여 들어가면 여기서
        // DB 값이 기본값으로 덮여 아래 단언이 깨진다.
        assertThat(repository.updateProfile(original.withProfile(
            "이름만 변경",
            null,
            "010-1234-5678",
            LocalDate.of(2000, 1, 2),
            null,
            null,
            null,
            Instant.parse("2026-09-19T00:00:00Z").truncatedTo(ChronoUnit.MICROS)
        ))).isTrue();
        assertThat(repository.findById(original.id()))
            .get()
            .extracting(
                User::name,
                User::emailNotificationEnabled,
                User::smsNotificationEnabled)
            .containsExactly("이름만 변경", false, true);
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

    @Test
    void updatesAdminMemoAndKeepsItThroughProfileUpdate() {
        User original = user(
            UserStatus.ACTIVE,
            "메모 대상",
            null,
            Instant.parse("2026-09-18T00:00:00Z"),
            Instant.parse("2026-09-18T00:00:00Z"),
            null
        );
        repository.save(original);
        Instant memoChangedAt = Instant.parse("2026-09-20T00:00:00Z")
            .truncatedTo(ChronoUnit.MICROS);

        assertThat(repository.updateAdminMemo(
            original.id(), "전화 문의 이력 있음", memoChangedAt)).isTrue();
        assertThat(repository.findById(original.id()))
            .get()
            .extracting(User::adminMemo, User::updatedAt)
            .containsExactly("전화 문의 이력 있음", memoChangedAt);
        // 저장 시점(2026-09-18)보다 확실히 늦은 시각으로 갱신됐는지 증분으로 본다.
        assertThat(memoChangedAt).isAfter(original.updatedAt());

        // updateProfile 의 SET 절에 admin_memo 가 섞여 있으면 프로필 수정만으로 메모가 지워진다.
        // 관리자 메모는 프로필 편집과 무관하게 살아남아야 한다.
        User existing = repository.findById(original.id()).orElseThrow();
        assertThat(repository.updateProfile(existing.withProfile(
            "이름만 변경",
            null,
            "010-1234-5678",
            LocalDate.of(2000, 1, 2),
            null,
            null,
            null,
            Instant.parse("2026-09-19T00:00:00Z").truncatedTo(ChronoUnit.MICROS)
        ))).isTrue();
        assertThat(repository.findById(original.id()))
            .get()
            .extracting(User::name, User::adminMemo)
            .containsExactly("이름만 변경", "전화 문의 이력 있음");
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
