package com.adn.dabaeum.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EnrollmentLockIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired UserRepository userRepository;

    @Test
    void executesCourseAndEnrollmentForUpdateQueriesAndRejectsStaleState() {
        Course course = course();
        User user = user();
        courseRepository.save(course);
        userRepository.save(user);
        Enrollment enrollment = enrollment(course.id(), user.id());
        enrollmentRepository.save(enrollment);

        assertThat(courseRepository.findActiveByIdForUpdate(course.id())).contains(course);
        assertThat(enrollmentRepository.findByIdForUpdate(enrollment.id())).contains(enrollment);
        assertThat(enrollmentRepository.updateState(
            enrollment, EnrollmentStatus.WAITLISTED)).isFalse();
    }

    private Course course() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        Institution institution = new Institution(UUID.randomUUID(), uniqueCode("INST"),
            "잠금 테스트 기관", null, null, null, null, null, InstitutionStatus.ACTIVE, now, now, null);
        institutionRepository.save(institution);
        return new Course(UUID.randomUUID(), institution.id(), uniqueCode("COURSE"), "잠금 과정",
            null, null, CourseEducationType.HYBRID, LocalDate.of(2099, 9, 1),
            LocalDate.of(2099, 10, 1), null, null, 1, null, null, false, null,
            CourseStatus.RECRUITING, now, now, null);
    }

    private User user() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        return new User(id, "잠금 사용자", "lock-" + id + "@example.com", null,
            LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now);
    }

    private Enrollment enrollment(UUID courseId, UUID userId) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new Enrollment(UUID.randomUUID(), courseId, userId, null,
            EnrollmentApplicationType.SELF, EnrollmentStatus.APPLIED, now, null, null,
            null, null, null, null, now, now);
    }
}
