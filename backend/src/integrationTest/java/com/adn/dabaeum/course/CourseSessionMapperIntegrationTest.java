package com.adn.dabaeum.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseEducationType;
import com.adn.dabaeum.course.domain.CourseRepository;
import com.adn.dabaeum.course.domain.CourseSession;
import com.adn.dabaeum.course.domain.CourseSessionPageCriteria;
import com.adn.dabaeum.course.domain.CourseSessionRepository;
import com.adn.dabaeum.course.domain.CourseSessionSort;
import com.adn.dabaeum.course.domain.CourseSessionSortDirection;
import com.adn.dabaeum.course.domain.CourseSessionStatus;
import com.adn.dabaeum.institution.domain.Institution;
import com.adn.dabaeum.institution.domain.InstitutionRepository;
import com.adn.dabaeum.institution.domain.InstitutionStatus;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CourseSessionMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    CourseSessionRepository repository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    InstitutionRepository institutionRepository;

    @Test
    void roundTripsNullableFieldsAndSupportsPageCountAndUpdate() {
        Course course = course();
        courseRepository.save(course);
        CourseSession first = session(course.id(), 1, null);
        CourseSession second = session(course.id(), 2, "강의실");
        repository.save(first);
        repository.save(second);

        assertThat(repository.findById(first.id())).contains(first);
        assertThat(repository.findByIdForUpdate(first.id())).contains(first);
        assertThat(repository.findByCourseIdAndSessionNo(course.id(), 1))
            .contains(first);
        assertThat(repository.findPageByCourseId(new CourseSessionPageCriteria(
            course.id(), 0, 10, CourseSessionSort.SESSION_NO,
            CourseSessionSortDirection.ASC
        ))).extracting(CourseSession::sessionNo).containsExactly(1, 2);
        assertThat(repository.countByCourseId(course.id())).isEqualTo(2);

        CourseSession updated = new CourseSession(
            first.id(), first.courseId(), first.sessionNo(), first.startsAt(), first.endsAt(),
            "온라인", first.attendanceOpensAt(), first.attendanceClosesAt(),
            CourseSessionStatus.OPEN, first.createdAt(), first.updatedAt().plusSeconds(1),
            first.topic()
        );
        assertThat(repository.update(updated)).isTrue();
        assertThat(repository.findById(first.id())).contains(updated);
        assertThat(repository.update(updatedWithUnknownId(updated))).isFalse();
    }

    @Test
    void allowsSameSessionNumberForDifferentCoursesAndRejectsDuplicateWithinCourse() {
        Course firstCourse = course();
        Course secondCourse = course();
        courseRepository.save(firstCourse);
        courseRepository.save(secondCourse);
        repository.save(session(firstCourse.id(), 1, null));
        repository.save(session(secondCourse.id(), 1, null));
        assertThat(repository.findByCourseIdAndSessionNo(secondCourse.id(), 1)).isPresent();

        assertSqlState("23505", () -> repository.save(
            session(firstCourse.id(), 1, "중복")
        ));
    }

    @Test
    void deletesAllSessionsForACourseWhenNoDependenciesExist() {
        Course course = course();
        courseRepository.save(course);
        repository.save(session(course.id(), 1, null));
        repository.save(session(course.id(), 2, "강의실"));

        assertThat(repository.existsSessionDependenciesByCourseId(course.id())).isFalse();

        repository.deleteAllByCourseId(course.id());

        assertThat(repository.countByCourseId(course.id())).isZero();
    }

    @Test
    void detectsDependencyWhenQrTokenReferencesSessionAndBlocksDeletion() {
        Course course = course();
        courseRepository.save(course);
        CourseSession sessionWithToken = session(course.id(), 1, null);
        repository.save(sessionWithToken);
        UUID issuerId = insertUser();
        Instant issuedAt = Instant.parse("2099-08-20T00:00:00Z");
        jdbcTemplate.update(
            """
                INSERT INTO tb_attendance_qr_tokens (
                    id, session_id, token_hash, issued_by, issued_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            UUID.randomUUID(), sessionWithToken.id(),
            "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
            issuerId, java.time.OffsetDateTime.ofInstant(issuedAt, java.time.ZoneOffset.UTC),
            java.time.OffsetDateTime.ofInstant(issuedAt.plusSeconds(30), java.time.ZoneOffset.UTC));

        assertThat(repository.existsSessionDependenciesByCourseId(course.id())).isTrue();

        assertSqlState("23503", () -> repository.deleteAllByCourseId(course.id()));
    }

    @Test
    void existsNonScheduledByCourseId_는_SCHEDULED만_있으면_false다() {
        Course course = course();
        courseRepository.save(course);
        repository.save(session(course.id(), 1, null));

        assertThat(repository.existsNonScheduledByCourseId(course.id())).isFalse();
    }

    @Test
    void existsNonScheduledByCourseId_는_COMPLETED_회차가_있으면_true다() {
        Course course = course();
        courseRepository.save(course);
        CourseSession scheduled = session(course.id(), 1, null);
        repository.save(scheduled);
        CourseSession completed = new CourseSession(
            UUID.randomUUID(), course.id(), 2, scheduled.startsAt(), scheduled.endsAt(), null,
            null, null, CourseSessionStatus.COMPLETED, scheduled.createdAt(),
            scheduled.updatedAt(), null);
        repository.save(completed);

        assertThat(repository.existsNonScheduledByCourseId(course.id())).isTrue();
    }

    @Test
    void reopensOnlyACompletedSessionBackToOpen() {
        Course course = course();
        courseRepository.save(course);
        CourseSession scheduled = session(course.id(), 1, null);
        CourseSession completed = new CourseSession(
            scheduled.id(), course.id(), 1, scheduled.startsAt(), scheduled.endsAt(), null,
            null, null, CourseSessionStatus.COMPLETED, scheduled.createdAt(),
            scheduled.updatedAt(), null);
        repository.save(completed);
        Instant now = scheduled.updatedAt().plusSeconds(60);

        assertThat(repository.reopen(completed.id(), CourseSessionStatus.COMPLETED,
            CourseSessionStatus.OPEN, now)).isTrue();
        assertThat(repository.findById(completed.id()).orElseThrow().status())
            .isEqualTo(CourseSessionStatus.OPEN);
        // 이미 OPEN 이면 기대한 현재 상태가 아니므로 갱신하지 않는다.
        assertThat(repository.reopen(completed.id(), CourseSessionStatus.COMPLETED,
            CourseSessionStatus.OPEN, now)).isFalse();
    }

    @Test
    void reordersSessionsViaOffsetShiftWithoutViolatingUniqueConstraint() {
        Course course = course();
        courseRepository.save(course);
        CourseSession first = session(course.id(), 1, null);
        CourseSession second = session(course.id(), 2, null);
        CourseSession third = session(course.id(), 3, null);
        repository.save(first);
        repository.save(second);
        repository.save(third);

        // 목표: 1<->2 를 맞바꾸고 3은 그대로 둔다 (second, first, third 순서).
        List<CourseSession> current = repository.findByCourseId(course.id());
        assertThat(current).extracting(CourseSession::sessionNo).containsExactly(1, 2, 3);

        int shifted = repository.shiftSessionNoByOffset(course.id(), 10_000);
        assertThat(shifted).isEqualTo(3);

        assertThat(repository.updateSessionNo(second.id(), 1)).isTrue();
        assertThat(repository.updateSessionNo(first.id(), 2)).isTrue();
        assertThat(repository.updateSessionNo(third.id(), 3)).isTrue();

        List<CourseSession> reordered = repository.findByCourseId(course.id());
        assertThat(reordered)
            .extracting(CourseSession::id, CourseSession::sessionNo)
            .containsExactly(
                org.assertj.core.api.Assertions.tuple(second.id(), 1),
                org.assertj.core.api.Assertions.tuple(first.id(), 2),
                org.assertj.core.api.Assertions.tuple(third.id(), 3)
            );
    }

    @Test
    void detectsDependencyWhenAttendanceRecordReferencesSession() {
        Course course = course();
        courseRepository.save(course);
        CourseSession sessionWithAttendance = session(course.id(), 1, null);
        repository.save(sessionWithAttendance);
        UUID userId = insertUser();
        UUID enrollmentId = insertEnrollment(course.id(), userId);
        jdbcTemplate.update(
            """
                INSERT INTO tb_attendance_records (
                    id, course_id, session_id, enrollment_id, attendance_method, status, source
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.randomUUID(), course.id(), sessionWithAttendance.id(), enrollmentId,
            "ADMIN", "PRESENT", "ADMIN_WEB");

        assertThat(repository.existsSessionDependenciesByCourseId(course.id())).isTrue();
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        String code = uniqueCode("cs-user");
        jdbcTemplate.update(
            "INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            id, code, code + "@example.test");
        return id;
    }

    private UUID insertEnrollment(UUID courseId, UUID userId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_enrollments (id, course_id, user_id, status) VALUES (?, ?, ?, ?)",
            id, courseId, userId, "APPROVED");
        return id;
    }

    private CourseSession updatedWithUnknownId(CourseSession source) {
        return new CourseSession(
            UUID.randomUUID(), source.courseId(), source.sessionNo(), source.startsAt(),
            source.endsAt(), source.location(), source.attendanceOpensAt(),
            source.attendanceClosesAt(), source.status(), source.createdAt(), source.updatedAt(),
            source.topic()
        );
    }

    private CourseSession session(UUID courseId, int number, String location) {
        Instant starts = Instant.parse("2099-09-01T00:00:00Z").plusSeconds(number * 86400L);
        Instant ends = starts.plusSeconds(7200);
        return new CourseSession(UUID.randomUUID(), courseId, number, starts, ends, location,
            null, null, CourseSessionStatus.SCHEDULED, starts.minusSeconds(86400), starts, null);
    }

    private Course course() {
        Instant now = Instant.parse("2099-08-01T00:00:00Z");
        Institution institution = new Institution(
            UUID.randomUUID(), uniqueCode("INST"), "세션 테스트 기관", null, null, null, null,
            null, InstitutionStatus.ACTIVE, now, now, null);
        institutionRepository.save(institution);
        return new Course(UUID.randomUUID(), institution.id(), uniqueCode("COURSE"), "세션 과정",
            null, null, CourseEducationType.HYBRID, java.time.LocalDate.of(2099, 9, 1),
            java.time.LocalDate.of(2099, 10, 1), null, null, 10, null, null, false, null,
            com.adn.dabaeum.course.domain.CourseStatus.DRAFT, now, now, null);
    }
}
