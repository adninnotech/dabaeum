package com.adn.dabaeum.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.course.domain.CourseStatus;
import com.adn.dabaeum.enrollment.domain.Enrollment;
import com.adn.dabaeum.enrollment.domain.EnrollmentApplicationType;
import com.adn.dabaeum.enrollment.domain.EnrollmentListItem;
import com.adn.dabaeum.enrollment.domain.EnrollmentPageCriteria;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentSort;
import com.adn.dabaeum.enrollment.domain.EnrollmentSortDirection;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 강좌별 수강신청 목록(selectListWithProfile)에 얹은 progressPercent·lastStudiedAt 이
 * 출석 집계와 맞고, 조인을 더해도 행 수가 늘지 않는지 확인한다.
 * 공유 dev DB 라 이 테스트가 만든 강좌·수강으로만 범위를 좁힌다.
 */
class EnrollmentListProgressIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseSessionRepository courseSessionRepository;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired UserRepository userRepository;

    @Test
    void 출석_기록이_있으면_진도율과_마지막_학습일이_계산되고_없으면_null이며_행_수는_그대로다() {
        Course course = course();
        courseRepository.save(course);
        List<CourseSession> sessions = addSessions(course.id(), 4);

        UUID learnerWithAttendance = user("출석학습자");
        UUID enrollmentWithAttendance = enroll(learnerWithAttendance, course.id());
        Instant firstCheckIn = Instant.parse("2099-08-05T00:00:00Z");
        Instant lastCheckIn = Instant.parse("2099-08-06T00:00:00Z");
        checkIn(course.id(), sessions.get(0).id(), enrollmentWithAttendance,
            AttendanceStatus.PRESENT, firstCheckIn);
        checkIn(course.id(), sessions.get(1).id(), enrollmentWithAttendance,
            AttendanceStatus.LATE, lastCheckIn);
        checkIn(course.id(), sessions.get(2).id(), enrollmentWithAttendance,
            AttendanceStatus.ABSENT, lastCheckIn.plusSeconds(60));

        UUID learnerWithoutAttendance = user("무출석학습자");
        UUID enrollmentWithoutAttendance = enroll(learnerWithoutAttendance, course.id());

        List<EnrollmentListItem> page = enrollmentRepository.findEnrollmentListByCourseId(
            new EnrollmentPageCriteria(
                course.id(), 0, 10, EnrollmentSort.USER_ID, EnrollmentSortDirection.ASC));

        assertThat(page).hasSize(2);
        assertThat(enrollmentRepository.countByCourseId(course.id())).isEqualTo(2);

        EnrollmentListItem withAttendance = page.stream()
            .filter(item -> item.enrollment().id().equals(enrollmentWithAttendance))
            .findFirst().orElseThrow();
        assertThat(withAttendance.progressPercent()).isEqualTo(50);
        assertThat(withAttendance.lastStudiedAt()).isEqualTo(lastCheckIn);

        EnrollmentListItem withoutAttendance = page.stream()
            .filter(item -> item.enrollment().id().equals(enrollmentWithoutAttendance))
            .findFirst().orElseThrow();
        assertThat(withoutAttendance.progressPercent()).isEqualTo(0);
        assertThat(withoutAttendance.lastStudiedAt()).isNull();
    }

    private void checkIn(
        UUID courseId, UUID sessionId, UUID enrollmentId, AttendanceStatus status, Instant checkedAt
    ) {
        attendanceRepository.save(new Attendance(
            UUID.randomUUID(), courseId, sessionId, enrollmentId, null,
            AttendanceMethod.ADMIN, status, checkedAt, AttendanceSource.ADMIN_WEB,
            null, checkedAt, checkedAt));
    }

    private List<CourseSession> addSessions(UUID courseId, int count) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return java.util.stream.IntStream.rangeClosed(1, count)
            .mapToObj(i -> {
                CourseSession session = new CourseSession(
                    UUID.randomUUID(), courseId, i,
                    now.plusSeconds(3600L * i), now.plusSeconds(3600L * i + 1800),
                    null, null, null, CourseSessionStatus.SCHEDULED, now, now, null);
                courseSessionRepository.save(session);
                return session;
            })
            .toList();
    }

    private UUID enroll(UUID userId, UUID courseId) {
        Instant appliedAt = Instant.parse("2099-08-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        enrollmentRepository.save(new Enrollment(
            id, courseId, userId, null, EnrollmentApplicationType.SELF,
            EnrollmentStatus.APPROVED, appliedAt, appliedAt, null, null, null, null, null,
            appliedAt, appliedAt));
        return id;
    }

    private UUID user(String name) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        UUID id = UUID.randomUUID();
        userRepository.save(new User(id, name, "user-" + id + "@example.com",
            null, LocalDate.of(1990, 1, 1), UserStatus.ACTIVE, null, now, now));
        return id;
    }

    private Course course() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        Institution institution = new Institution(UUID.randomUUID(), uniqueCode("INST"),
            "수강현황 진도 테스트 기관", null, null, null, null, null,
            InstitutionStatus.ACTIVE, now, now, null);
        institutionRepository.save(institution);
        return new Course(UUID.randomUUID(), institution.id(), uniqueCode("COURSE"),
            "수강현황 진도 과정", null, null, CourseEducationType.HYBRID,
            LocalDate.of(2099, 9, 1), LocalDate.of(2099, 10, 1), null, null, 10, null, null,
            false, null, CourseStatus.RECRUITING, now, now, null);
    }
}
