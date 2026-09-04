package com.adn.dabaeum.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.role.application.AssignRoleCommand;
import com.adn.dabaeum.role.application.RoleApplicationService;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RoleApplicationIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    RoleApplicationService service;

    @Autowired
    UserRepository userRepository;

    @Autowired
    InstitutionRepository institutionRepository;

    @Test
    void assignsListsAndRevokesInstitutionScopedRoles() {
        User user = user(UserStatus.ACTIVE);
        Institution institution = institution();
        userRepository.save(user);
        institutionRepository.save(institution);

        UserRoleAssignment learner = service.assign(new AssignRoleCommand(
            user.id(),
            UserRole.LEARNER,
            institution.id()
        ));
        UserRoleAssignment instructor = service.assign(new AssignRoleCommand(
            user.id(),
            UserRole.INSTRUCTOR,
            institution.id()
        ));

        assertThat(service.list(user.id()))
            .extracting(UserRoleAssignment::role)
            .containsExactlyInAnyOrder(learner.role(), instructor.role());
        assertThat(service.revoke(user.id(), learner.id())).isEqualTo(learner);
        assertThat(service.list(user.id())).containsExactly(instructor);
    }

    @Test
    void rejectsDuplicateAssignmentWithRoleConflict() {
        User user = user(UserStatus.ACTIVE);
        Institution institution = institution();
        userRepository.save(user);
        institutionRepository.save(institution);
        service.assign(new AssignRoleCommand(
            user.id(),
            UserRole.INSTRUCTOR,
            institution.id()
        ));

        assertThatThrownBy(() -> service.assign(new AssignRoleCommand(
            user.id(),
            UserRole.INSTRUCTOR,
            institution.id()
        ))).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.code())
                .isEqualTo(ApiErrorCode.ROLE_ASSIGNMENT_CONFLICT);
        });
    }

    @Test
    void rejectsWithdrawnUserRoleAccess() {
        User user = user(UserStatus.WITHDRAWN);
        userRepository.save(user);

        assertThatThrownBy(() -> service.list(user.id()))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.code())
                    .isEqualTo(ApiErrorCode.USER_STATUS_FORBIDDEN);
            });
    }

    @Test
    void protectsTheLastRequiredRole() {
        User user = user(UserStatus.ACTIVE);
        userRepository.save(user);
        UserRoleAssignment global = service.assign(new AssignRoleCommand(
            user.id(),
            UserRole.PLATFORM_ADMIN,
            null
        ));

        assertThatThrownBy(() -> service.revoke(user.id(), global.id()))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.code()).isEqualTo(ApiErrorCode.ROLE_REQUIRED);
            });
    }

    private User user(UserStatus status) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new User(
            UUID.randomUUID(),
            "Role API integration user",
            null,
            null,
            LocalDate.of(1990, 1, 1),
            status,
            status == UserStatus.WITHDRAWN ? now : null,
            now,
            now
        );
    }

    private Institution institution() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new Institution(
            UUID.randomUUID(),
            "ROLE-API-" + UUID.randomUUID(),
            "Role API integration institution",
            null,
            null,
            null,
            null,
            null,
            InstitutionStatus.ACTIVE,
            now,
            now,
            null
        );
    }
}
