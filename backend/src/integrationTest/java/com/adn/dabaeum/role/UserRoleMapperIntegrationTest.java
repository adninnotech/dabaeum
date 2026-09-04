package com.adn.dabaeum.role;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.role.domain.UserRole;
import com.adn.dabaeum.role.domain.UserRoleAssignment;
import com.adn.dabaeum.role.domain.UserRoleRepository;
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

class UserRoleMapperIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Autowired
    UserRoleRepository roleRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    InstitutionRepository institutionRepository;

    @Test
    void savesAndFindsGlobalAndInstitutionScopedRoles() {
        User user = user();
        Institution institution = institution();
        userRepository.save(user);
        institutionRepository.save(institution);
        UserRoleAssignment global = assignment(
            user.id(),
            null,
            UserRole.PLATFORM_ADMIN,
            "2090-08-01T00:00:00Z"
        );
        UserRoleAssignment scoped = assignment(
            user.id(),
            institution.id(),
            UserRole.INSTRUCTOR,
            "2090-08-02T00:00:00Z"
        );

        roleRepository.save(global);
        roleRepository.save(scoped);

        assertThat(roleRepository.findById(global.id())).contains(global);
        assertThat(roleRepository.findByUserId(user.id()))
            .containsExactly(global, scoped);
        assertThat(roleRepository.findByUserIdAndInstitution(
            user.id(),
            institution.id()
        )).containsExactly(scoped);
    }

    @Test
    void ordersRolesByCreatedAtThenId() {
        User user = user();
        Institution institution = institution();
        userRepository.save(user);
        institutionRepository.save(institution);
        UserRoleAssignment later = assignment(
            user.id(),
            institution.id(),
            UserRole.INSTRUCTOR,
            "2091-08-02T00:00:00Z"
        );
        UserRoleAssignment earlier = assignment(
            user.id(),
            institution.id(),
            UserRole.LEARNER,
            "2091-08-01T00:00:00Z"
        );
        roleRepository.save(later);
        roleRepository.save(earlier);

        assertThat(roleRepository.findByUserId(user.id()))
            .containsExactly(earlier, later);
    }

    @Test
    void rejectsDuplicateScopedAssignmentWithUniqueViolation() {
        User user = user();
        Institution institution = institution();
        userRepository.save(user);
        institutionRepository.save(institution);
        roleRepository.save(assignment(
            user.id(),
            institution.id(),
            UserRole.INSTRUCTOR,
            "2092-08-01T00:00:00Z"
        ));
        assertSqlState("23505", () -> roleRepository.save(assignment(
            user.id(),
            institution.id(),
            UserRole.INSTRUCTOR,
            "2092-08-02T00:00:00Z"
        )));
    }

    @Test
    void conflictSafeInsertConvergesWithoutAbortingTransaction() {
        User user = user();
        Institution institution = institution();
        userRepository.save(user);
        institutionRepository.save(institution);
        UserRoleAssignment first = assignment(
            user.id(),
            institution.id(),
            UserRole.INSTRUCTOR,
            "2092-08-05T00:00:00Z"
        );
        UserRoleAssignment duplicate = assignment(
            user.id(),
            institution.id(),
            UserRole.INSTRUCTOR,
            "2092-08-06T00:00:00Z"
        );

        assertThat(roleRepository.saveIfAbsent(first)).isTrue();
        assertThat(roleRepository.saveIfAbsent(duplicate)).isFalse();
        assertThat(roleRepository.findByUserIdAndInstitution(
            user.id(),
            institution.id()
        )).containsExactly(first);
        assertThat(roleRepository.findByIdForUpdate(first.id())).contains(first);
        assertThat(roleRepository.findByUserIdAndInstitutionForUpdate(
            user.id(),
            institution.id()
        )).containsExactly(first);
    }

    @Test
    void rejectsDuplicateGlobalAssignmentWithUniqueViolation() {
        User user = user();
        userRepository.save(user);
        roleRepository.save(assignment(
            user.id(),
            null,
            UserRole.PLATFORM_ADMIN,
            "2092-08-03T00:00:00Z"
        ));
        assertSqlState("23505", () -> roleRepository.save(assignment(
            user.id(),
            null,
            UserRole.PLATFORM_ADMIN,
            "2092-08-04T00:00:00Z"
        )));
    }

    @Test
    void rejectsMissingUserWithForeignKeyViolation() {
        UUID missingUser = UUID.randomUUID();
        assertSqlState("23503", () -> roleRepository.save(assignment(
            missingUser,
            null,
            UserRole.PLATFORM_ADMIN,
            "2093-08-01T00:00:00Z"
        )));

    }

    @Test
    void rejectsMissingInstitutionWithForeignKeyViolation() {
        UUID missingInstitution = UUID.randomUUID();
        User user = user();
        userRepository.save(user);
        assertSqlState("23503", () -> roleRepository.save(assignment(
            user.id(),
            missingInstitution,
            UserRole.INSTRUCTOR,
            "2093-08-02T00:00:00Z"
        )));
    }

    private User user() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new User(
            UUID.randomUUID(),
            "Role test user",
            null,
            null,
            LocalDate.of(1990, 1, 1),
            UserStatus.ACTIVE,
            null,
            now,
            now
        );
    }

    private Institution institution() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new Institution(
            UUID.randomUUID(),
            "ROLE-" + UUID.randomUUID(),
            "Role test institution",
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

    private UserRoleAssignment assignment(
        UUID userId,
        UUID institutionId,
        UserRole role,
        String createdAt
    ) {
        return new UserRoleAssignment(
            UUID.randomUUID(),
            userId,
            institutionId,
            role,
            Instant.parse(createdAt)
        );
    }
}
