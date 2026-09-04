package com.adn.dabaeum.role.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.course.domain.CourseInstructorRepository;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleApplicationServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID ROLE_ID = UUID.fromString(
        "22222222-2222-2222-2222-222222222222"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "33333333-3333-3333-3333-333333333333"
    );
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Mock
    UserRoleRepository roleRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    InstitutionRepository institutionRepository;

    @Mock
    RoleIdGenerator idGenerator;

    @Mock
    CourseInstructorRepository courseInstructorRepository;

    private RoleApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultRoleApplicationService(
            roleRepository,
            userRepository,
            institutionRepository,
            courseInstructorRepository,
            idGenerator,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void assignsGlobalAndInstitutionScopedRoles() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(roleRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(roleRepository.findByUserIdAndInstitution(
            USER_ID,
            INSTITUTION_ID
        )).thenReturn(List.of());
        when(institutionRepository.findById(INSTITUTION_ID))
            .thenReturn(Optional.of(mock(Institution.class)));
        when(idGenerator.generate()).thenReturn(ROLE_ID);

        service.assign(new AssignRoleCommand(
            USER_ID,
            UserRole.PLATFORM_ADMIN,
            null
        ));
        service.assign(new AssignRoleCommand(
            USER_ID,
            UserRole.INSTRUCTOR,
            INSTITUTION_ID
        ));
    }

    @Test
    void rejectsWithdrawnUsers() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(
            user(UserStatus.WITHDRAWN)
        ));

        assertThatThrownBy(() -> service.list(USER_ID))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.code())
                    .isEqualTo(ApiErrorCode.USER_STATUS_FORBIDDEN);
            });
    }

    @Test
    void rejectsDuplicateGlobalAssignment() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(roleRepository.findByUserId(USER_ID)).thenReturn(List.of(
            assignment(UserRole.PLATFORM_ADMIN, null)
        ));

        assertThatThrownBy(() -> service.assign(new AssignRoleCommand(
            USER_ID,
            UserRole.PLATFORM_ADMIN,
            null
        ))).isInstanceOfSatisfying(ApiException.class, exception -> {
            org.assertj.core.api.Assertions.assertThat(exception.code())
                .isEqualTo(ApiErrorCode.ROLE_ASSIGNMENT_CONFLICT);
        });
    }

    @Test
    void rejectsInstitutionScopeForGlobalPlatformAdmin() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> service.assign(new AssignRoleCommand(
            USER_ID,
            UserRole.PLATFORM_ADMIN,
            INSTITUTION_ID
        ))).isInstanceOfSatisfying(ApiException.class, exception -> {
            org.assertj.core.api.Assertions.assertThat(exception.code())
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        });
    }

    @Test
    void refusesToRemoveTheLastRequiredRole() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(roleRepository.findByIdForUpdate(ROLE_ID)).thenReturn(Optional.of(
            assignment(UserRole.PLATFORM_ADMIN, null)
        ));
        when(roleRepository.findByUserId(USER_ID)).thenReturn(List.of(
            assignment(UserRole.PLATFORM_ADMIN, null)
        ));

        assertThatThrownBy(() -> service.revoke(USER_ID, ROLE_ID))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.code())
                    .isEqualTo(ApiErrorCode.ROLE_REQUIRED);
            });
    }

    @Test
    void refusesToRevokeInstructorRoleWhileCourseAssignmentRemains() {
        UserRoleAssignment learner = new UserRoleAssignment(
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            USER_ID,
            null,
            UserRole.LEARNER,
            NOW
        );
        UserRoleAssignment instructor = assignment(
            UserRole.INSTRUCTOR,
            INSTITUTION_ID
        );
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(roleRepository.findByIdForUpdate(ROLE_ID))
            .thenReturn(Optional.of(instructor));
        when(roleRepository.findByUserId(USER_ID)).thenReturn(List.of(
            learner,
            instructor
        ));
        when(courseInstructorRepository.existsByUserIdAndInstitutionId(
            USER_ID,
            INSTITUTION_ID
        )).thenReturn(true);

        assertThatThrownBy(() -> service.revoke(USER_ID, ROLE_ID))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.code())
                    .isEqualTo(ApiErrorCode.ROLE_ASSIGNMENT_CONFLICT);
            });
        verify(roleRepository).findByIdForUpdate(ROLE_ID);
        verify(roleRepository, never()).deleteByIdAndUserId(ROLE_ID, USER_ID);
    }

    private User user() {
        return user(UserStatus.ACTIVE);
    }

    private User user(UserStatus status) {
        return new User(
            USER_ID,
            "Role user",
            null,
            null,
            LocalDate.of(1990, 1, 1),
            status,
            status == UserStatus.WITHDRAWN ? NOW : null,
            NOW,
            NOW
        );
    }

    private UserRoleAssignment assignment(
        UserRole role,
        UUID institutionId
    ) {
        return new UserRoleAssignment(
            ROLE_ID,
            USER_ID,
            institutionId,
            role,
            NOW
        );
    }
}
