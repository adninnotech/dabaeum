package com.adn.dabaeum.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.domain.Gender;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserListFilter;
import com.adn.dabaeum.user.domain.UserListItem;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
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
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock
    UserRepository repository;

    @Mock
    UserIdGenerator idGenerator;

    @Mock
    UserRoleRepository userRoleRepository;

    private UserApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultUserApplicationService(
            repository,
            idGenerator,
            Clock.fixed(NOW, ZoneOffset.UTC),
            org.mockito.Mockito.mock(com.adn.dabaeum.file.domain.StoredFileRepository.class),
            userRoleRepository,
            org.mockito.Mockito.mock(UserInterestService.class)
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

        assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(repository, idGenerator);
    }

    @Test
    void rejectsNullCommand() {
        ApiException exception = catchThrowableOfType(
            () -> service.create(null),
            ApiException.class
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(exception.code()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(repository, idGenerator);
    }

    @Test
    void listsUsersWithTypedCriteriaAndTotalPages() {
        List<User> users = List.of(user("첫 사용자"), user("둘 사용자"));
        when(repository.findPage(any())).thenReturn(
            users.stream().map(u -> new UserListItem(u, 3L, 5L)).toList());
        when(repository.count(UserListFilter.basic(UserStatus.ACTIVE, null, null))).thenReturn(21L);

        UserPage page = service.list(new ListUsersQuery(
            2,
            10,
            "name,asc",
            UserStatus.ACTIVE,
            null,
            null,
            null, null, null, null, null
        ));

        ArgumentCaptor<UserPageCriteria> criteria =
            ArgumentCaptor.forClass(UserPageCriteria.class);
        verify(repository).findPage(criteria.capture());
        verify(repository).count(UserListFilter.basic(UserStatus.ACTIVE, null, null));
        assertThat(criteria.getValue().offset()).isEqualTo(20);
        assertThat(criteria.getValue().limit()).isEqualTo(10);
        assertThat(criteria.getValue().sort()).isEqualTo(UserSort.NAME);
        assertThat(criteria.getValue().direction()).isEqualTo(UserSortDirection.ASC);
        assertThat(criteria.getValue().filter().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(page.data())
            .extracting(UserView::user)
            .containsExactlyElementsOf(users);
        assertThat(page.data())
            .extracting(UserView::enrollmentCount, UserView::courseCount)
            .containsOnly(org.assertj.core.groups.Tuple.tuple(3L, 5L));
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void 목록은_역할을_한_번에_가져온다() {
        List<User> users = IntStream.range(0, 20)
            .mapToObj(index -> user("사용자 " + index))
            .toList();
        when(repository.findPage(any())).thenReturn(
            users.stream().map(u -> new UserListItem(u, 0L, 0L)).toList());
        when(repository.count(UserListFilter.basic(null, null, null))).thenReturn(20L);
        when(userRoleRepository.findByUserIds(any())).thenReturn(Map.of(
            users.get(0).id(),
            List.of(new RoleSummary(UserRole.INSTRUCTOR, INSTITUTION_ID, "테스트기관"))
        ));

        UserPage page = service.list(
            listQuery(0, 20, "createdAt,desc")
        );

        assertThat(page.data()).hasSize(20);
        // 수강도 강의 배정도 없는 사용자는 null 이 아니라 0 이다.
        assertThat(page.data().get(0).enrollmentCount()).isZero();
        assertThat(page.data().get(0).courseCount()).isZero();
        assertThat(page.data().get(0).roles())
            .containsExactly(new RoleSummary(
                UserRole.INSTRUCTOR,
                INSTITUTION_ID,
                "테스트기관"
            ));
        // 역할이 없는 사용자도 null 이 아니라 빈 목록을 받는다.
        assertThat(page.data().get(1).roles()).isEmpty();
        // 20명이어도 역할 질의는 한 번. 사용자마다 부르면 그게 N+1 이다.
        verify(userRoleRepository, times(1)).findByUserIds(any());
        verify(userRoleRepository, never()).findByUserId(any());
    }

    @Test
    void 빈_페이지는_역할을_아예_묻지_않는다() {
        when(repository.findPage(any())).thenReturn(List.of());
        when(repository.count(UserListFilter.basic(null, null, null))).thenReturn(0L);

        UserPage page = service.list(
            listQuery(0, 20, "createdAt,desc")
        );

        assertThat(page.data()).isEmpty();
        verify(userRoleRepository, never()).findByUserIds(any());
        verify(userRoleRepository, never()).findByUserId(any());
    }

    @Test
    void rejectsMalformedListQueryWithoutRepositoryCall() {
        for (ListUsersQuery query : List.of(
            listQuery(-1, 20, "createdAt,desc"),
            listQuery(0, 0, "createdAt,desc"),
            listQuery(0, 101, "createdAt,desc"),
            listQuery(0, 20, "password,desc"),
            listQuery(Integer.MAX_VALUE, 2, "createdAt,desc")
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
    void 새_필터를_서울_날짜_경계와_접속_구간으로_바꿔_목록과_합계에_똑같이_넘긴다() {
        when(repository.findPage(any())).thenReturn(List.of());

        service.list(new ListUsersQuery(
            0, 10, "lastLoginAt,desc", null, null, UserRole.LEARNER,
            Gender.FEMALE, true, UserLastLoginFilter.WITHIN_7,
            LocalDate.parse("2026-07-06"), LocalDate.parse("2026-08-04")
        ));

        ArgumentCaptor<UserPageCriteria> criteria =
            ArgumentCaptor.forClass(UserPageCriteria.class);
        verify(repository).findPage(criteria.capture());
        UserListFilter filter = criteria.getValue().filter();
        assertThat(criteria.getValue().sort()).isEqualTo(UserSort.LAST_LOGIN_AT);
        assertThat(filter.role()).isEqualTo(UserRole.LEARNER);
        assertThat(filter.gender()).isEqualTo(Gender.FEMALE);
        assertThat(filter.hasEnrollment()).isTrue();
        // NOW 는 2026-08-04T00:00Z(서울 09:00). 최근 7일은 그 시각부터 7일 전 이후다.
        assertThat(filter.lastLoginFrom()).isEqualTo(Instant.parse("2026-07-28T00:00:00Z"));
        assertThat(filter.lastLoginBefore()).isNull();
        assertThat(filter.lastLoginNever()).isFalse();
        // 가입일은 서울 날짜 양 끝 포함: 07-06 00:00 KST 이상, 08-05 00:00 KST 미만.
        assertThat(filter.createdFrom()).isEqualTo(Instant.parse("2026-07-05T15:00:00Z"));
        assertThat(filter.createdBefore()).isEqualTo(Instant.parse("2026-08-04T15:00:00Z"));
        // 합계도 같은 조건이어야 페이지 수가 맞다.
        verify(repository).count(filter);
    }

    @Test
    void 오래된_접속과_접속_기록_없음을_구분한다() {
        when(repository.findPage(any())).thenReturn(List.of());

        service.list(lastLoginQuery(UserLastLoginFilter.OVER_90));
        service.list(lastLoginQuery(UserLastLoginFilter.NEVER));

        ArgumentCaptor<UserPageCriteria> criteria =
            ArgumentCaptor.forClass(UserPageCriteria.class);
        verify(repository, times(2)).findPage(criteria.capture());
        UserListFilter over90 = criteria.getAllValues().get(0).filter();
        assertThat(over90.lastLoginBefore()).isEqualTo(Instant.parse("2026-05-06T00:00:00Z"));
        assertThat(over90.lastLoginFrom()).isNull();
        assertThat(over90.lastLoginNever()).isFalse();
        UserListFilter never = criteria.getAllValues().get(1).filter();
        assertThat(never.lastLoginNever()).isTrue();
        assertThat(never.lastLoginFrom()).isNull();
        assertThat(never.lastLoginBefore()).isNull();
    }

    @Test
    void 수강_건수_정렬을_받는다() {
        when(repository.findPage(any())).thenReturn(List.of());

        service.list(listQuery(0, 10, "enrollmentCount,asc"));

        ArgumentCaptor<UserPageCriteria> criteria =
            ArgumentCaptor.forClass(UserPageCriteria.class);
        verify(repository).findPage(criteria.capture());
        assertThat(criteria.getValue().sort()).isEqualTo(UserSort.ENROLLMENT_COUNT);
        assertThat(criteria.getValue().direction()).isEqualTo(UserSortDirection.ASC);
    }

    @Test
    void 거꾸로_된_가입일은_400이다() {
        for (ListUsersQuery query : List.of(
            new ListUsersQuery(0, 10, "createdAt,desc", null, null, null,
                null, null, null, LocalDate.parse("2026-08-05"), LocalDate.parse("2026-08-04"))
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

    private static ListUsersQuery listQuery(int page, int size, String sort) {
        return new ListUsersQuery(page, size, sort, null, null, null,
            null, null, null, null, null);
    }

    private static ListUsersQuery lastLoginQuery(UserLastLoginFilter lastLogin) {
        return new ListUsersQuery(0, 10, "createdAt,desc", null, null, null,
            null, null, lastLogin, null, null);
    }

    @Test
    void returnsUserByIdAndMapsMissingUserToNotFound() {
        User user = user("조회 사용자");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user));
        assertThat(service.get(USER_ID).user()).isSameAs(user);

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
