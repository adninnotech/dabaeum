package com.adn.dabaeum.instructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.authentication.application.LocalJwtTokenService;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedTokenDetails;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.application.AssignCourseInstructorCommand;
import com.adn.dabaeum.course.application.CourseInstructorApplicationService;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseInstructorRole;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.instructor.application.ApplyInstructorCommand;
import com.adn.dabaeum.instructor.application.InstructorApplicationService;
import com.adn.dabaeum.instructor.application.InstructorApplicationView;
import com.adn.dabaeum.instructor.domain.InstructorApplicationStatus;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

class InstructorWorkflowIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Autowired InstructorApplicationService applications;
    @Autowired CourseInstructorApplicationService courseInstructors;
    @Autowired RoleApplicationService roles;
    @Autowired UserRepository users;
    @Autowired InstitutionRepository institutions;
    @Autowired CourseRepository courses;
    @Autowired LocalJwtTokenService localJwtTokenService;

    @Test
    void appliesApprovesAssignsAndProtectsScopedInstructorRole() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Institution institution = new Institution(
            UUID.randomUUID(), uniqueCode("I-INST"), "강사 통합 기관",
            null, null, null, null, null, InstitutionStatus.ACTIVE,
            now, now, null
        );
        User applicant = user("강사 신청자", now);
        User manager = user("플랫폼 관리자", now);
        Course course = new Course(
            UUID.randomUUID(), institution.id(), uniqueCode("I-COURSE"),
            "강사 배정 과정", null, null, CourseEducationType.HYBRID,
            LocalDate.of(2099, 9, 1), LocalDate.of(2099, 10, 1),
            null, null, 20, null, null, false, null,
            CourseStatus.DRAFT, now, now, null
        );
        institutions.save(institution);
        users.save(applicant);
        users.save(manager);
        courses.save(course);
        roles.assign(new AssignRoleCommand(applicant.id(), UserRole.LEARNER, null));

        InstructorApplicationView pending = applications.apply(
            new ApplyInstructorCommand(institution.id(), "현장 강의 경력 보유"),
            learner(applicant.id())
        );
        InstructorApplicationView approved = applications.approve(
            pending.id(),
            platformAdmin(manager.id())
        );

        assertThat(approved.status()).isEqualTo(
            InstructorApplicationStatus.APPROVED
        );
        UserRoleAssignment instructorRole = roles.list(applicant.id()).stream()
            .filter(role -> role.role() == UserRole.INSTRUCTOR)
            .findFirst()
            .orElseThrow();
        assertThat(instructorRole.institutionId()).isEqualTo(institution.id());
        Authentication refreshedAuthentication = localJwtTokenService.authenticate(
            localJwtTokenService.issue(applicant.id()).value()
        );
        AuthenticatedTokenDetails refreshedToken =
            (AuthenticatedTokenDetails) refreshedAuthentication.getDetails();
        assertThat(refreshedToken.context().roles()).contains(
            new AuthenticatedRole("LEARNER", null),
            new AuthenticatedRole("INSTRUCTOR", institution.id())
        );

        courseInstructors.assign(
            new AssignCourseInstructorCommand(
                course.id(), applicant.id(), CourseInstructorRole.MAIN
            ),
            platformAdmin(manager.id())
        );
        assertThatThrownBy(() -> roles.revoke(
            applicant.id(),
            instructorRole.id()
        )).isInstanceOfSatisfying(ApiException.class, exception ->
            assertThat(exception.code()).isEqualTo(
                ApiErrorCode.ROLE_ASSIGNMENT_CONFLICT
            )
        );

        courseInstructors.remove(
            course.id(), applicant.id(), platformAdmin(manager.id())
        );
        assertThat(roles.revoke(applicant.id(), instructorRole.id()))
            .isEqualTo(instructorRole);
        assertThat(roles.list(applicant.id()))
            .extracting(UserRoleAssignment::role)
            .containsExactly(UserRole.LEARNER);
    }

    private User user(String name, Instant now) {
        UUID id = UUID.randomUUID();
        return new User(
            id, name, "instructor-workflow-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now
        );
    }

    private AuthenticatedUserContext learner(UUID userId) {
        return new AuthenticatedUserContext(
            userId, "LOCAL", Set.of(new AuthenticatedRole("LEARNER", null))
        );
    }

    private AuthenticatedUserContext platformAdmin(UUID userId) {
        return new AuthenticatedUserContext(
            userId,
            "LOCAL",
            Set.of(new AuthenticatedRole("PLATFORM_ADMIN", null))
        );
    }
}
