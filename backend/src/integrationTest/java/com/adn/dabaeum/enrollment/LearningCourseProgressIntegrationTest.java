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
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.enrollment.domain.LearningCourseView;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.user.domain.User;
import com.adn.dabaeum.user.domain.UserRepository;
import com.adn.dabaeum.user.domain.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 학습 목록 한 번 조회에 출석 집계(attendedCount·progressPercent)가 조인으로 딸려 오는지,
 * 행마다 추가 쿼리 없이 여러 강좌를 한 번에 맞게 계산하는지 확인한다. 공유 dev DB 라 이 테스트가
 * 만든 강좌·수강으로만 범위를 좁힌다.
 */
class LearningCourseProgressIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired EnrollmentQueryRepository enrollmentQueryRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseSessionRepository courseSessionRepository;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired UserRepository userRepository;

    @Test
    void 여러_강좌를_한_번에_조회해도_강좌마다_출석수와_진도율이_맞는다() {
        UUID learnerId = user("진도학습자");

        Course courseA = course("출석 있는 교실");
        courseRepository.save(courseA);
        List<CourseSession> sessionsA = addSessions(courseA.id(), 5);
        UUID enrollmentA = enroll(learnerId, courseA.id());
        checkIn(courseA.id(), sessionsA.get(0).id(), enrollmentA, AttendanceStatus.PRESENT);
        checkIn(courseA.id(), sessionsA.get(1).id(), enrollmentA, AttendanceStatus.LATE);
        checkIn(courseA.id(), sessionsA.get(2).id(), enrollmentA, AttendanceStatus.ABSENT);

        Course courseB = course("회차 없는 교실");
        courseRepository.save(courseB);
        UUID enrollmentB = enroll(learnerId, courseB.id());

        List<LearningCourseView> found = enrollmentQueryRepository.findLearningCourses(
            learnerId, null, null, 50, 0);

        LearningCourseView viewA = found.stream()
            .filter(v -> v.enrollmentId().equals(enrollmentA)).findFirst().orElseThrow();
        assertThat(viewA.attendedCount()).isEqualTo(2);
        assertThat(viewA.sessionCount()).isEqualTo(5);
        assertThat(viewA.progressPercent()).isEqualTo(40);

        LearningCourseView viewB = found.stream()
            .filter(v -> v.enrollmentId().equals(enrollmentB)).findFirst().orElseThrow();
        assertThat(viewB.attendedCount()).isEqualTo(0);
        assertThat(viewB.sessionCount()).isEqualTo(0);
        assertThat(viewB.progressPercent()).isEqualTo(0);
    }

    private void checkIn(
        UUID courseId, UUID sessionId, UUID enrollmentId, AttendanceStatus status
    ) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        attendanceRepository.save(new Attendance(
            UUID.randomUUID(), courseId, sessionId, enrollmentId, null,
            AttendanceMethod.ADMIN, status, now, AttendanceSource.ADMIN_WEB,
            null, now, now));
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

    private Course course(String title) {
        Institution institution = new Institution(
            UUID.randomUUID(),
            uniqueCode("INST"),
            "진도 테스트 기관",
            "123-45-67890",
            "홍길동",
            "서울시",
            "02-1234-5678",
            "progress@example.com",
            InstitutionStatus.ACTIVE,
            Instant.parse("2099-08-01T00:00:00Z"),
            Instant.parse("2099-08-01T00:00:00Z"),
            null
        );
        institutionRepository.save(institution);

        return new Course(
            UUID.randomUUID(),
            institution.id(),
            uniqueCode("COURSE"),
            title,
            "설명",
            "TSIX_040102",
            CourseEducationType.OFFLINE,
            LocalDate.of(2099, 9, 1),
            LocalDate.of(2099, 10, 31),
            LocalDate.of(2099, 8, 1),
            LocalDate.of(2099, 8, 25),
            20,
            "서울 교육장",
            null,
            false,
            null,
            CourseStatus.RECRUITING,
            Instant.parse("2099-08-01T00:00:00Z"),
            Instant.parse("2099-08-02T00:00:00Z"),
            null
        );
    }
}
