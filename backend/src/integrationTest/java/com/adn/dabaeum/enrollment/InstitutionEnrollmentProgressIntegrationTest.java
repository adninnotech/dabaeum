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
import com.adn.dabaeum.enrollment.domain.EnrollmentQueryRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentRepository;
import com.adn.dabaeum.enrollment.domain.EnrollmentSort;
import com.adn.dabaeum.enrollment.domain.EnrollmentSortDirection;
import com.adn.dabaeum.enrollment.domain.EnrollmentStatus;
import com.adn.dabaeum.enrollment.domain.InstitutionEnrollmentView;
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
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사이트 전체 수강현황(/admin/enrollments, /institutions/{id}/enrollments) 목록에 얹은
 * progressPercent·lastStudiedAt 이 강좌별 목록과 같은 숫자를 주고, 조인을 더해도 행 수가
 * 늘지 않는지 확인한다.
 */
class InstitutionEnrollmentProgressIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired EnrollmentQueryRepository queryRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseSessionRepository courseSessionRepository;
    @Autowired AttendanceRepository attendanceRepository;
    @Autowired InstitutionRepository institutionRepository;
    @Autowired UserRepository userRepository;

    @Test
    void 사이트_전체_목록의_진도율과_최근_학습일이_강좌별_목록과_같고_행_수는_신청_건수_그대로다() {
        Institution institution = institution();
        institutionRepository.save(institution);
        Course course = course(institution.id());
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

        List<InstitutionEnrollmentView> allEnrollments = queryRepository.findAllEnrollments(
            null, institution.id(), course.id(), null, null, 10, 0, "appliedAt,asc");
        List<InstitutionEnrollmentView> institutionEnrollments =
            queryRepository.findInstitutionEnrollments(
                institution.id(), null, 10, 0, "appliedAt,asc");

        assertThat(allEnrollments).hasSize(2);
        assertThat(institutionEnrollments).hasSize(2);
        assertThat(queryRepository.countAllEnrollments(null, institution.id(), course.id(), null, null))
            .isEqualTo(2);
        assertThat(queryRepository.countInstitutionEnrollments(institution.id(), null))
            .isEqualTo(2);

        InstitutionEnrollmentView withAttendance = allEnrollments.stream()
            .filter(view -> view.id().equals(enrollmentWithAttendance))
            .findFirst().orElseThrow();
        assertThat(withAttendance.progressPercent()).isEqualTo(50);
        assertThat(withAttendance.lastStudiedAt()).isEqualTo(lastCheckIn);

        InstitutionEnrollmentView withoutAttendance = allEnrollments.stream()
            .filter(view -> view.id().equals(enrollmentWithoutAttendance))
            .findFirst().orElseThrow();
        assertThat(withoutAttendance.progressPercent()).isEqualTo(0);
        assertThat(withoutAttendance.lastStudiedAt()).isNull();

        // 강좌별 목록과 같은 수강 건에 대해 같은 숫자를 준다 — 두 화면이 같은 값을 보여야 한다.
        List<EnrollmentListItem> byCourse = enrollmentRepository.findEnrollmentListByCourseId(
            new EnrollmentPageCriteria(
                course.id(), 0, 10, EnrollmentSort.USER_ID, EnrollmentSortDirection.ASC));
        EnrollmentListItem courseItemWithAttendance = byCourse.stream()
            .filter(item -> item.enrollment().id().equals(enrollmentWithAttendance))
            .findFirst().orElseThrow();
        assertThat(withAttendance.progressPercent())
            .isEqualTo(courseItemWithAttendance.progressPercent());
        assertThat(withAttendance.lastStudiedAt())
            .isEqualTo(courseItemWithAttendance.lastStudiedAt());
    }

    @Test
    void 기관_목록의_대기_순번은_필터와_페이지에_상관없이_강좌_전체_대기자_기준이고_처리일은_마지막_처리다() {
        Institution institution = institution();
        institutionRepository.save(institution);
        Course course = course(institution.id());
        courseRepository.save(course);
        Instant base = Instant.parse("2099-08-01T00:00:00Z");

        UUID firstWaiting = enrollAs(user("대기1"), course.id(), EnrollmentStatus.WAITLISTED,
            base.plusSeconds(10), null, null);
        UUID secondWaiting = enrollAs(user("대기2"), course.id(), EnrollmentStatus.WAITLISTED,
            base.plusSeconds(20), null, null);
        Instant approvedAt = base.plusSeconds(100);
        Instant withdrawnAt = base.plusSeconds(200);
        UUID withdrawn = enrollAs(user("철회"), course.id(), EnrollmentStatus.WITHDRAWN,
            base.plusSeconds(5), approvedAt, withdrawnAt);
        UUID approved = enrollAs(user("승인"), course.id(), EnrollmentStatus.APPROVED,
            base.plusSeconds(1), approvedAt, null);

        // 상태 필터로 대기자만, 한 쪽에 1건씩 가져와도 순번은 강좌 전체 대기열 기준이다.
        List<InstitutionEnrollmentView> secondPage = queryRepository.findInstitutionEnrollments(
            institution.id(), EnrollmentStatus.WAITLISTED, 1, 1, "appliedAt,asc");
        assertThat(secondPage).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(secondWaiting);
            assertThat(view.waitlistPosition()).isEqualTo(2);
            assertThat(view.processedAt()).isNull();
        });

        List<InstitutionEnrollmentView> all = queryRepository.findAllEnrollments(
            null, institution.id(), course.id(), null, null, 10, 0, "appliedAt,asc");
        assertThat(byId(all, firstWaiting).waitlistPosition()).isEqualTo(1);
        assertThat(byId(all, approved).waitlistPosition()).isNull();
        assertThat(byId(all, approved).processedAt()).isEqualTo(approvedAt);
        // 승인 뒤 철회는 마지막 처리인 철회 시각을 보인다(강좌별 목록 Enrollment.processedAt 과 같은 규칙).
        assertThat(byId(all, withdrawn).processedAt()).isEqualTo(withdrawnAt);
    }

    private static InstitutionEnrollmentView byId(List<InstitutionEnrollmentView> views, UUID id) {
        return views.stream().filter(view -> view.id().equals(id)).findFirst().orElseThrow();
    }

    private UUID enrollAs(
        UUID userId, UUID courseId, EnrollmentStatus status, Instant appliedAt,
        Instant approvedAt, Instant withdrawnAt
    ) {
        UUID id = UUID.randomUUID();
        enrollmentRepository.save(new Enrollment(
            id, courseId, userId, null, EnrollmentApplicationType.SELF,
            status, appliedAt, approvedAt, null, null, withdrawnAt, null, null,
            appliedAt, appliedAt));
        return id;
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
        return IntStream.rangeClosed(1, count)
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

    private Institution institution() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new Institution(UUID.randomUUID(), uniqueCode("INST"),
            "사이트전체 수강현황 진도 테스트 기관", null, null, null, null, null,
            InstitutionStatus.ACTIVE, now, now, null);
    }

    private Course course(UUID institutionId) {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        return new Course(UUID.randomUUID(), institutionId, uniqueCode("COURSE"),
            "사이트전체 수강현황 진도 과정", null, null, CourseEducationType.HYBRID,
            LocalDate.of(2099, 9, 1), LocalDate.of(2099, 10, 1), null, null, 10, null, null,
            false, null, CourseStatus.RECRUITING, now, now, null);
    }
}
