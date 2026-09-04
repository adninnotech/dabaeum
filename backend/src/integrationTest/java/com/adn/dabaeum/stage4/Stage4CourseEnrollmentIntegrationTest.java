package com.adn.dabaeum.stage4;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.application.CreateEnrollmentCommand;
import com.adn.dabaeum.enrollment.application.CreateProxyEnrollmentCommand;
import com.adn.dabaeum.enrollment.application.EnrollmentApplicationService;
import com.adn.dabaeum.enrollment.application.RejectEnrollmentCommand;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
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

class Stage4CourseEnrollmentIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired CourseRepository courseRepository;
    @Autowired CourseSessionRepository sessionRepository;
    @Autowired EnrollmentApplicationService enrollmentService;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleRepository roleRepository;

    @Test
    void acceptsCourseSessionAndEnrollmentLifecycleFlowsInsideRollback() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        Institution institution = institution(now);
        User manager = user("manager", now);
        User learner = user("learner", now);
        User proxyLearner = user("proxy", now);
        User queuedLearner = user("queued", now);
        institutionRepository.save(institution);
        userRepository.save(manager);
        userRepository.save(learner);
        userRepository.save(proxyLearner);
        userRepository.save(queuedLearner);
        roleRepository.save(role(manager, institution, UserRole.INSTITUTION_ADMIN, now));
        roleRepository.save(role(learner, institution, UserRole.LEARNER, now));
        roleRepository.save(role(proxyLearner, institution, UserRole.LEARNER, now));
        roleRepository.save(role(queuedLearner, institution, UserRole.LEARNER, now));

        Course lifecycleCourse = course(institution.id(), 10, CourseStatus.DRAFT, now);
        courseRepository.save(lifecycleCourse);
        Course recruiting = replaceStatus(lifecycleCourse, CourseStatus.RECRUITING, now);
        assertThat(courseRepository.updateActive(recruiting)).isTrue();
        Course closed = replaceStatus(recruiting, CourseStatus.RECRUITMENT_CLOSED, now);
        assertThat(courseRepository.updateActive(closed)).isTrue();
        assertThat(courseRepository.findById(lifecycleCourse.id())).get()
            .extracting(Course::status).isEqualTo(CourseStatus.RECRUITMENT_CLOSED);

        CourseSession session = new CourseSession(UUID.randomUUID(), lifecycleCourse.id(), 1,
            Instant.parse("2099-09-01T01:00:00Z"), Instant.parse("2099-09-01T02:00:00Z"),
            "서울 교육장", null, null, CourseSessionStatus.SCHEDULED, now, now);
        sessionRepository.save(session);
        assertThat(sessionRepository.update(new CourseSession(session.id(), session.courseId(),
            session.sessionNo(), session.startsAt(), session.endsAt(), session.location(), null, null,
            CourseSessionStatus.OPEN, now, now.plusSeconds(60)))).isTrue();
        CourseSession completed = new CourseSession(session.id(), session.courseId(), session.sessionNo(),
            session.startsAt(), session.endsAt(), session.location(), null, null,
            CourseSessionStatus.COMPLETED, now, now.plusSeconds(120));
        assertThat(sessionRepository.update(completed)).isTrue();

        Course enrollmentCourse = course(institution.id(), 2, CourseStatus.RECRUITING, now);
        courseRepository.save(enrollmentCourse);
        AuthenticatedUserContext managerContext = context(manager, "INSTITUTION_ADMIN", institution);
        AuthenticatedUserContext learnerContext = context(learner, "LEARNER", institution);
        AuthenticatedUserContext proxyContext = context(proxyLearner, "LEARNER", institution);
        AuthenticatedUserContext queuedContext = context(queuedLearner, "LEARNER", institution);

        Enrollment self = enrollmentService.createSelf(
            new CreateEnrollmentCommand(enrollmentCourse.id(), learner.id(), null), learnerContext);
        assertThat(enrollmentService.approve(self.id(), managerContext).status())
            .isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(enrollmentService.withdraw(self.id(), learnerContext).status())
            .isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(enrollmentService.createSelf(
            new CreateEnrollmentCommand(enrollmentCourse.id(), learner.id(), null), learnerContext)
            .status()).isEqualTo(EnrollmentStatus.APPLIED);

        Enrollment proxy = enrollmentService.createProxy(
            new CreateProxyEnrollmentCommand(enrollmentCourse.id(), proxyLearner.id()), managerContext);
        assertThat(enrollmentService.reject(
            new RejectEnrollmentCommand(proxy.id(), "외부 사유"), managerContext).status())
            .isEqualTo(EnrollmentStatus.REJECTED);
        assertThat(enrollmentService.createProxy(
            new CreateProxyEnrollmentCommand(enrollmentCourse.id(), proxyLearner.id()), managerContext)
            .status()).isEqualTo(EnrollmentStatus.APPLIED);

        Course queueCourse = course(institution.id(), 1, CourseStatus.RECRUITING, now);
        courseRepository.save(queueCourse);
        Enrollment queueFirst = enrollmentService.createSelf(
            new CreateEnrollmentCommand(queueCourse.id(), learner.id(), null), learnerContext);
        enrollmentService.approve(queueFirst.id(), managerContext);
        Enrollment queued = enrollmentService.createSelf(
            new CreateEnrollmentCommand(queueCourse.id(), queuedLearner.id(), null), queuedContext);
        assertThat(queued.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(enrollmentService.cancel(queued.id(), queuedContext).status())
            .isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(enrollmentService.createSelf(
            new CreateEnrollmentCommand(queueCourse.id(), queuedLearner.id(), null), queuedContext)
            .status()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    private Institution institution(Instant now) {
        return new Institution(UUID.randomUUID(), uniqueCode("INST"), "Stage 4 기관", null, null,
            null, null, null, InstitutionStatus.ACTIVE, now, now, null);
    }

    private User user(String prefix, Instant now) {
        UUID id = UUID.randomUUID();
        return new User(id, prefix, prefix + "-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now);
    }

    private UserRoleAssignment role(User user, Institution institution, UserRole role, Instant now) {
        return new UserRoleAssignment(UUID.randomUUID(), user.id(), institution.id(), role, now);
    }

    private Course course(UUID institutionId, int capacity, CourseStatus status, Instant now) {
        return new Course(UUID.randomUUID(), institutionId, uniqueCode("COURSE"), "Stage 4 과정",
            null, null, CourseEducationType.HYBRID, LocalDate.of(2099, 9, 1),
            LocalDate.of(2099, 10, 1), null, null, capacity, null, null, false, null,
            status, now, now, null);
    }

    private Course replaceStatus(Course course, CourseStatus status, Instant now) {
        return new Course(course.id(), course.institutionId(), course.courseCode(), course.title(),
            course.description(), course.category(), course.educationType(), course.startDate(),
            course.endDate(), course.recruitStartDate(), course.recruitEndDate(), course.capacity(),
            course.location(), course.onlineUrl(), course.creditBankEligible(), course.creditValue(),
            status, course.createdAt(), now, course.deletedAt());
    }

    private AuthenticatedUserContext context(User user, String role, Institution institution) {
        return new AuthenticatedUserContext(user.id(), "LOCAL",
            Set.of(new AuthenticatedRole(role, institution.id())));
    }
}
