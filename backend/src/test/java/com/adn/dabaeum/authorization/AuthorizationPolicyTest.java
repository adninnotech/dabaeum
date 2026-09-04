package com.adn.dabaeum.authorization;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.authorization.application.AuthorizationPolicy;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthorizationPolicyTest {

    private static final UUID USER_ID = UUID.fromString(
        "11111111-1111-1111-1111-111111111111"
    );

    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    @Test
    void allowsOnlyGlobalPlatformAdmin() {
        assertThatCode(() -> policy.requirePlatformAdmin(context(
            new AuthenticatedRole("PLATFORM_ADMIN", null)
        ))).doesNotThrowAnyException();
    }

    @Test
    void rejectsInstitutionScopedPlatformAdmin() {
        assertThatThrownBy(() -> policy.requirePlatformAdmin(context(
            new AuthenticatedRole(
                "PLATFORM_ADMIN",
                UUID.fromString("22222222-2222-2222-2222-222222222222")
            )
        ))).isInstanceOfSatisfying(ApiException.class, exception -> {
            org.assertj.core.api.Assertions.assertThat(exception.status())
                .isEqualTo(HttpStatus.FORBIDDEN);
            org.assertj.core.api.Assertions.assertThat(exception.code())
                .isEqualTo(ApiErrorCode.FORBIDDEN);
        });
    }

    @Test
    void rejectsInstitutionRolesAndLearner() {
        assertForbidden(new AuthenticatedRole(
            "INSTITUTION_ADMIN",
            UUID.fromString("22222222-2222-2222-2222-222222222222")
        ));
        assertForbidden(new AuthenticatedRole(
            "INSTRUCTOR",
            UUID.fromString("22222222-2222-2222-2222-222222222222")
        ));
        assertForbidden(new AuthenticatedRole(
            "LEARNER",
            UUID.fromString("22222222-2222-2222-2222-222222222222")
        ));
    }

    @Test
    void allowsGlobalPlatformAdminAndSameInstitutionAdminForCourse() {
        UUID institutionId = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
        );

        assertThatCode(() -> policy.requireCourseManager(
            context(new AuthenticatedRole("PLATFORM_ADMIN", null)),
            institutionId
        )).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCourseManager(
            context(new AuthenticatedRole("INSTITUTION_ADMIN", institutionId)),
            institutionId
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsDifferentInstitutionCourseManagerScope() {
        UUID institutionId = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
        );
        assertThatThrownBy(() -> policy.requireCourseManager(
            context(new AuthenticatedRole(
                "INSTITUTION_ADMIN",
                UUID.fromString("44444444-4444-4444-4444-444444444444")
            )),
            institutionId
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(exception.code()).isEqualTo(
                ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
        });
    }

    @Test
    void allowsSessionManagerOnlyForGlobalAdminScopedAdminOrAssignedMainInstructor() {
        UUID institutionId = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
        );
        assertThatCode(() -> policy.requireCourseSessionManager(
            context(new AuthenticatedRole("PLATFORM_ADMIN", null)),
            institutionId,
            false
        )).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCourseSessionManager(
            context(new AuthenticatedRole("INSTITUTION_ADMIN", institutionId)),
            institutionId,
            false
        )).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCourseSessionManager(
            context(new AuthenticatedRole("INSTRUCTOR", institutionId)),
            institutionId,
            true
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnassignedOrOutOfScopeSessionInstructor() {
        UUID institutionId = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
        );
        AuthenticatedUserContext sameInstitutionInstructor = context(
            new AuthenticatedRole("INSTRUCTOR", institutionId));
        AuthenticatedUserContext otherInstitutionInstructor = context(
            new AuthenticatedRole("INSTRUCTOR",
                UUID.fromString("44444444-4444-4444-4444-444444444444")));
        AuthenticatedUserContext learner = context(
            new AuthenticatedRole("LEARNER", institutionId));
        for (AuthenticatedUserContext context : new AuthenticatedUserContext[] {
            sameInstitutionInstructor, otherInstitutionInstructor, learner
        }) {
            assertThatThrownBy(() -> policy.requireCourseSessionManager(
                context, institutionId,
                context == sameInstitutionInstructor ? false : true
            )).isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(exception.code()).isEqualTo(
                    ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
            });
        }
    }

    @Test
    void allowsEnrollmentReaderForManagerOrAssignedInstructor() {
        UUID institutionId = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
        assertThatCode(() -> policy.requireEnrollmentReader(
            context(new AuthenticatedRole("PLATFORM_ADMIN", null)), institutionId, false))
            .doesNotThrowAnyException();
        assertThatCode(() -> policy.requireEnrollmentReader(
            context(new AuthenticatedRole("INSTITUTION_ADMIN", institutionId)), institutionId, false))
            .doesNotThrowAnyException();
        assertThatCode(() -> policy.requireEnrollmentReader(
            context(new AuthenticatedRole("INSTRUCTOR", institutionId)), institutionId, true))
            .doesNotThrowAnyException();
    }

    @Test
    void allowsEnrollmentSubjectAndRejectsOtherLearner() {
        UUID institutionId = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
        assertThatCode(() -> policy.requireEnrollmentSubjectOrReader(
            context(new AuthenticatedRole("LEARNER", institutionId)), USER_ID,
            institutionId, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireEnrollmentSubjectOrReader(
            context(new AuthenticatedRole("LEARNER", institutionId)),
            UUID.fromString("99999999-9999-9999-9999-999999999999"), institutionId, false))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(exception.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
            });
    }

    @Test
    void allowsEnrollmentDecisionManagerOnlyForGlobalAdminScopedAdminOrMainInstructor() {
        UUID institutionId = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
        assertThatCode(() -> policy.requireEnrollmentDecisionManager(
            context(new AuthenticatedRole("PLATFORM_ADMIN", null)), institutionId, false))
            .doesNotThrowAnyException();
        assertThatCode(() -> policy.requireEnrollmentDecisionManager(
            context(new AuthenticatedRole("INSTITUTION_ADMIN", institutionId)), institutionId, false))
            .doesNotThrowAnyException();
        assertThatCode(() -> policy.requireEnrollmentDecisionManager(
            context(new AuthenticatedRole("INSTRUCTOR", institutionId)), institutionId, true))
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireEnrollmentDecisionManager(
            context(new AuthenticatedRole("INSTRUCTOR", institutionId)), institutionId, false))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(exception.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
            });
    }

    @Test
    void allowsEnrollmentSubjectOrDecisionManager() {
        UUID institutionId = UUID.fromString(
            "22222222-2222-2222-2222-222222222222");
        assertThatCode(() -> policy.requireEnrollmentSubjectOrDecisionManager(
            context(new AuthenticatedRole("LEARNER", institutionId)), USER_ID,
            institutionId, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireEnrollmentSubjectOrDecisionManager(
            context(new AuthenticatedRole("LEARNER", institutionId)),
            UUID.fromString("99999999-9999-9999-9999-999999999999"), institutionId, false))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(exception.code()).isEqualTo(ApiErrorCode.INSTITUTION_SCOPE_FORBIDDEN);
            });
    }

    private void assertForbidden(AuthenticatedRole role) {
        assertThatThrownBy(() -> policy.requirePlatformAdmin(context(role)))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.status())
                    .isEqualTo(HttpStatus.FORBIDDEN);
                org.assertj.core.api.Assertions.assertThat(exception.code())
                    .isEqualTo(ApiErrorCode.FORBIDDEN);
            });
    }

    private AuthenticatedUserContext context(AuthenticatedRole role) {
        return new AuthenticatedUserContext(USER_ID, "DADAEGU", Set.of(role));
    }
}
