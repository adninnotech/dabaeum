package com.adn.dabaeum.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.application.EnrollmentApplicationService;
import com.adn.dabaeum.enrollment.application.RejectEnrollmentCommand;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EnrollmentLifecycleIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired EnrollmentApplicationService service;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository userRoleRepository;

    @Test
    void appliesExpectedTransitionsAndAllowsTerminalResubmission() {
        Fixture approved = fixture(10);
        Enrollment applied = service.createSelf(
            new com.adn.dabaeum.enrollment.application.CreateEnrollmentCommand(
                approved.course.id(), approved.learner.id(), EnrollmentApplicationType.SELF),
            approved.learnerContext());
        Enrollment approvedState = service.approve(applied.id(), approved.managerContext());
        assertThat(approvedState.status()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollmentRepository.findById(applied.id())).get()
            .extracting(Enrollment::status).isEqualTo(EnrollmentStatus.APPROVED);

        Fixture rejected = fixture(10);
        Enrollment rejectedApplied = self(rejected);
        Enrollment rejectedState = service.reject(
            new RejectEnrollmentCommand(rejectedApplied.id(), "  요건 미충족  "),
            rejected.managerContext());
        assertThat(rejectedState.status()).isEqualTo(EnrollmentStatus.REJECTED);
        Enrollment rejectedResubmission = self(rejected);
        assertThat(rejectedResubmission.id()).isNotEqualTo(rejectedApplied.id());

        Fixture waitlisted = fixture(1);
        Enrollment first = self(waitlisted);
        service.approve(first.id(), waitlisted.managerContext());
        Enrollment queued = service.createSelf(
            new com.adn.dabaeum.enrollment.application.CreateEnrollmentCommand(
                waitlisted.course.id(), waitlisted.secondLearner.id(), null),
            waitlisted.secondLearnerContext());
        assertThat(queued.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
        Enrollment cancelled = service.cancel(queued.id(), waitlisted.secondLearnerContext());
        assertThat(cancelled.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        Enrollment waitlistedResubmission = service.createSelf(
            new com.adn.dabaeum.enrollment.application.CreateEnrollmentCommand(
                waitlisted.course.id(), waitlisted.secondLearner.id(), null),
            waitlisted.secondLearnerContext());
        assertThat(waitlistedResubmission.status()).isEqualTo(EnrollmentStatus.WAITLISTED);

        Fixture withdrawn = fixture(10);
        Enrollment approvedForWithdrawal = self(withdrawn);
        service.approve(approvedForWithdrawal.id(), withdrawn.managerContext());
        Enrollment withdrawnState = service.withdraw(
            approvedForWithdrawal.id(), withdrawn.learnerContext());
        assertThat(withdrawnState.status()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(self(withdrawn).status()).isEqualTo(EnrollmentStatus.APPLIED);
    }

    private Enrollment self(Fixture fixture) {
        return service.createSelf(
            new com.adn.dabaeum.enrollment.application.CreateEnrollmentCommand(
                fixture.course.id(), fixture.learner.id(), null), fixture.learnerContext());
    }

    private Fixture fixture(int capacity) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        Institution institution = new Institution(UUID.randomUUID(), uniqueCode("INST"),
            "Lifecycle 기관", null, null, null, null, null, InstitutionStatus.ACTIVE, now, now, null);
        institutionRepository.save(institution);
        User learner = user("learner");
        User secondLearner = user("second-learner");
        User manager = user("manager");
        userRepository.save(learner);
        userRepository.save(secondLearner);
        userRepository.save(manager);
        userRoleRepository.save(new UserRoleAssignment(UUID.randomUUID(), learner.id(),
            institution.id(), UserRole.LEARNER, now));
        userRoleRepository.save(new UserRoleAssignment(UUID.randomUUID(), secondLearner.id(),
            institution.id(), UserRole.LEARNER, now));
        userRoleRepository.save(new UserRoleAssignment(UUID.randomUUID(), manager.id(),
            institution.id(), UserRole.INSTITUTION_ADMIN, now));
        Course course = new Course(UUID.randomUUID(), institution.id(), uniqueCode("COURSE"),
            "Lifecycle 과정", null, null, CourseEducationType.HYBRID,
            LocalDate.of(2099, 9, 1), LocalDate.of(2099, 10, 1), null, null,
            capacity, null, null, false, null, CourseStatus.RECRUITING, now, now, null);
        courseRepository.save(course);
        return new Fixture(institution, course, learner, secondLearner, manager);
    }

    private User user(String prefix) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        return new User(id, prefix, prefix + "-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now);
    }

    private record Fixture(
        Institution institution,
        Course course,
        User learner,
        User secondLearner,
        User manager
    ) {
        AuthenticatedUserContext learnerContext() {
            return new AuthenticatedUserContext(learner.id(), "LOCAL",
                Set.of(new AuthenticatedRole("LEARNER", institution.id())));
        }

        AuthenticatedUserContext managerContext() {
            return new AuthenticatedUserContext(manager.id(), "LOCAL",
                Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", institution.id())));
        }

        AuthenticatedUserContext secondLearnerContext() {
            return new AuthenticatedUserContext(secondLearner.id(), "LOCAL",
                Set.of(new AuthenticatedRole("LEARNER", institution.id())));
        }
    }
}
