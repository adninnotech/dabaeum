package com.adn.dabaeum.administrator.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.administrator.domain.Administrator;
import com.adn.dabaeum.administrator.domain.AdministratorPage;
import com.adn.dabaeum.administrator.domain.AdministratorRepository;
import com.adn.dabaeum.authentication.domain.LocalAccountCredential;
import com.adn.dabaeum.authentication.domain.LocalAccountRepository;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.identity.application.IdentityIdGenerator;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.role.application.RoleIdGenerator;
import com.adn.dabaeum.role.domain.RoleSummary;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
import com.adn.dabaeum.user.application.UserIdGenerator;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 설계서 §10.1 — 관리자 계정 생성·목록. */
@ExtendWith(MockitoExtension.class)
class AdministratorServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INSTITUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock AdministratorRepository administrators;
    @Mock UserRepository users;
    @Mock LocalAccountRepository accounts;
    @Mock UserRoleRepository roles;
    @Mock InstitutionRepository institutions;
    @Mock UserIdGenerator userIds;
    @Mock IdentityIdGenerator identityIds;
    @Mock RoleIdGenerator roleIds;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AdministratorService service;

    @BeforeEach
    void setUp() {
        service = new AdministratorService(administrators, users, accounts, roles, institutions,
            userIds, identityIds, roleIds, passwordEncoder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsInstitutionAdminWithTemporaryPassword() {
        when(institutions.findActiveById(INSTITUTION_ID)).thenReturn(Optional.of(Mockito.mock(Institution.class)));
        when(accounts.findByNormalizedEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userIds.generate()).thenReturn(USER_ID);
        when(identityIds.generate()).thenReturn(UUID.randomUUID());
        when(roleIds.generate()).thenReturn(UUID.randomUUID());
        when(administrators.findById(USER_ID, NOW)).thenReturn(Optional.of(row(USER_ID)));
        when(roles.findByUserIds(List.of(USER_ID))).thenReturn(Map.of(USER_ID,
            List.of(new RoleSummary(UserRole.INSTITUTION_ADMIN, INSTITUTION_ID, "기관"))));

        CreatedAdministrator created = service.create(new CreateAdministratorCommand(
            " 기관 관리자 ", " Admin@Example.com ", UserRole.INSTITUTION_ADMIN, INSTITUTION_ID));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().name()).isEqualTo("기관 관리자");
        ArgumentCaptor<LocalAccountCredential> credential = ArgumentCaptor.forClass(LocalAccountCredential.class);
        verify(accounts).save(credential.capture());
        assertThat(passwordEncoder.matches(created.temporaryPassword(), credential.getValue().passwordHash())).isTrue();
        verify(accounts).markPasswordTemporary(USER_ID);
        ArgumentCaptor<UserRoleAssignment> role = ArgumentCaptor.forClass(UserRoleAssignment.class);
        verify(roles).save(role.capture());
        assertThat(role.getValue().role()).isEqualTo(UserRole.INSTITUTION_ADMIN);
        assertThat(role.getValue().institutionId()).isEqualTo(INSTITUTION_ID);
        assertThat(created.administrator().roles()).singleElement()
            .satisfies(summary -> assertThat(summary.role()).isEqualTo(UserRole.INSTITUTION_ADMIN));
        assertThat(created.toString()).doesNotContain(created.temporaryPassword());
    }

    @Test
    void platformAdminMustNotHaveInstitution() {
        assertCode(() -> service.create(new CreateAdministratorCommand(
            "관리자", "a@example.com", UserRole.PLATFORM_ADMIN, INSTITUTION_ID)), ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void institutionAdminNeedsInstitution() {
        assertCode(() -> service.create(new CreateAdministratorCommand(
            "관리자", "a@example.com", UserRole.INSTITUTION_ADMIN, null)), ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void nonAdminRoleIsRejected() {
        assertCode(() -> service.create(new CreateAdministratorCommand(
            "관리자", "a@example.com", UserRole.LEARNER, null)), ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void unknownInstitutionIsNotFound() {
        when(institutions.findActiveById(INSTITUTION_ID)).thenReturn(Optional.empty());

        assertCode(() -> service.create(new CreateAdministratorCommand(
            "관리자", "a@example.com", UserRole.INSTITUTION_ADMIN, INSTITUTION_ID)), ApiErrorCode.INSTITUTION_NOT_FOUND);
        verify(users, never()).save(any());
    }

    @Test
    void duplicateEmailConflicts() {
        when(accounts.findByNormalizedEmail("a@example.com")).thenReturn(Optional.of(Mockito.mock(LocalAccountCredential.class)));

        assertCode(() -> service.create(new CreateAdministratorCommand(
            "관리자", "a@example.com", UserRole.PLATFORM_ADMIN, null)), ApiErrorCode.IDENTITY_CONFLICT);
        verify(users, never()).save(any());
    }

    @Test
    void listFillsRolesInOneQuery() {
        UUID other = UUID.randomUUID();
        when(administrators.search(any())).thenReturn(new AdministratorPage(List.of(row(USER_ID), row(other)), 2));
        when(roles.findByUserIds(List.of(USER_ID, other))).thenReturn(Map.of(
            USER_ID, List.of(new RoleSummary(UserRole.PLATFORM_ADMIN, null, null))));

        AdministratorPage page = service.list(0, 20, null, null, "관리");

        assertThat(page.administrators()).hasSize(2);
        assertThat(page.administrators().get(0).roles()).hasSize(1);
        assertThat(page.administrators().get(1).roles()).isEmpty();
        verify(roles).findByUserIds(anyList());
    }

    @Test
    void listRejectsBadPagingAndNonAdminRole() {
        assertCode(() -> service.list(0, 101, null, null, null), ApiErrorCode.BAD_REQUEST);
        assertCode(() -> service.list(0, 20, UserRole.LEARNER, null, null), ApiErrorCode.VALIDATION_FAILED);
    }

    private static Administrator row(UUID userId) {
        return new Administrator(userId, "관리자", "admin@example.com", UserStatus.ACTIVE,
            List.of(), null, null, null, NOW);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ApiErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
