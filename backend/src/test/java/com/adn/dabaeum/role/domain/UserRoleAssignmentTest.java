package com.adn.dabaeum.role.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserRoleAssignmentTest {

    private static final UUID ID = UUID.fromString(
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    );
    private static final UUID USER_ID = UUID.fromString(
        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    );
    private static final UUID INSTITUTION_ID = UUID.fromString(
        "cccccccc-cccc-cccc-cccc-cccccccccccc"
    );
    private static final Instant CREATED_AT =
        Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void supportsOnlyApprovedRoles() {
        assertThat(UserRole.values()).containsExactly(
            UserRole.LEARNER,
            UserRole.INSTITUTION_ADMIN,
            UserRole.INSTRUCTOR,
            UserRole.PLATFORM_ADMIN
        );
    }

    @Test
    void requiresInstitutionForScopedRoles() {
        UserRoleAssignment assignment = new UserRoleAssignment(
            ID,
            USER_ID,
            INSTITUTION_ID,
            UserRole.INSTRUCTOR,
            CREATED_AT
        );

        assertThat(assignment.institutionId()).isEqualTo(INSTITUTION_ID);
        assertThatThrownBy(() -> new UserRoleAssignment(
            ID,
            USER_ID,
            null,
            UserRole.INSTRUCTOR,
            CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsLearnerWithoutInstitution() {
        assertThatCode(() -> new UserRoleAssignment(
            ID,
            USER_ID,
            null,
            UserRole.LEARNER,
            CREATED_AT
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsInstitutionOnPlatformAdminAndMissingRequiredValues() {
        assertThatThrownBy(() -> new UserRoleAssignment(
            ID,
            USER_ID,
            INSTITUTION_ID,
            UserRole.PLATFORM_ADMIN,
            CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new UserRoleAssignment(
            ID,
            USER_ID,
            null,
            UserRole.PLATFORM_ADMIN,
            CREATED_AT
        )).doesNotThrowAnyException();
        assertThatThrownBy(() -> new UserRoleAssignment(
            null,
            USER_ID,
            null,
            UserRole.PLATFORM_ADMIN,
            CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
