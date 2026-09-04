package com.adn.dabaeum.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendancePageCriteria;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceSort;
import com.adn.dabaeum.attendance.domain.AttendanceSortDirection;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.attendance.domain.AttendanceQrToken;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AttendanceMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    AttendanceRepository attendanceRepository;

    @Autowired
    AttendanceQrTokenRepository tokenRepository;

    @Test
    void roundTripsQrAndAdminAttendanceAndSupportsEnrollmentLookup() {
        Fixture fixture = fixture();
        Instant createdAt = Instant.parse("2026-08-05T00:00:00Z");
        UUID tokenId = UUID.randomUUID();
        tokenRepository.save(new AttendanceQrToken(
            tokenId,
            fixture.sessionId(),
            "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
            fixture.userId(),
            createdAt,
            createdAt.plusSeconds(30),
            null,
            createdAt
        ));
        Attendance qr = attendance(
            fixture, fixture.enrollmentId(), tokenId, AttendanceMethod.QR,
            AttendanceStatus.PRESENT, AttendanceSource.APP,
            createdAt, createdAt.plusSeconds(1)
        );
        attendanceRepository.save(qr);

        Attendance admin = attendance(
            fixture, fixture.adminEnrollmentId(), null, AttendanceMethod.ADMIN,
            AttendanceStatus.EXCUSED, AttendanceSource.ADMIN_WEB,
            createdAt.plusSeconds(2), createdAt.plusSeconds(3)
        );
        attendanceRepository.save(admin);

        assertThat(attendanceRepository.findById(qr.id())).contains(qr);
        assertThat(attendanceRepository.findByIdForUpdate(qr.id())).contains(qr);
        assertThat(attendanceRepository.findBySessionAndEnrollment(
            fixture.sessionId(), fixture.enrollmentId())).contains(qr);
        assertThat(attendanceRepository.findByEnrollment(fixture.enrollmentId()))
            .containsExactly(qr);
        assertThat(attendanceRepository.countBySession(fixture.sessionId())).isEqualTo(2);
    }

    @Test
    void pagesByAllowlistedCheckedAtSortAndRejectsStaleStatusUpdate() {
        Fixture fixture = fixture();
        Instant base = Instant.parse("2026-08-05T00:00:00Z");
        Attendance first = attendance(
            fixture, fixture.enrollmentId(), null, AttendanceMethod.ADMIN,
            AttendanceStatus.PRESENT, AttendanceSource.ADMIN_WEB,
            base.plusSeconds(20), base.plusSeconds(21)
        );
        Attendance second = attendance(
            fixture, fixture.adminEnrollmentId(), null, AttendanceMethod.ADMIN,
            AttendanceStatus.LATE, AttendanceSource.ADMIN_WEB,
            base.plusSeconds(10), base.plusSeconds(11)
        );
        attendanceRepository.save(first);
        attendanceRepository.save(second);

        List<Attendance> page = attendanceRepository.findBySession(
            fixture.sessionId(), new AttendancePageCriteria(
                fixture.sessionId(), 0, 1,
                AttendanceSort.CHECKED_AT, AttendanceSortDirection.ASC));

        assertThat(page).containsExactly(second);
        assertThat(attendanceRepository.updateStatus(
            first.id(), AttendanceStatus.LATE, AttendanceStatus.ABSENT,
            base.plusSeconds(30))).isFalse();
        assertThat(attendanceRepository.updateStatus(
            first.id(), AttendanceStatus.PRESENT, AttendanceStatus.ABSENT,
            base.plusSeconds(30))).isTrue();
        assertThat(attendanceRepository.findById(first.id()))
            .get().extracting(Attendance::status).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    void rejectsDuplicateSessionEnrollmentAttendance() {
        Fixture fixture = fixture();
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        Attendance original = attendance(
            fixture, fixture.enrollmentId(), null, AttendanceMethod.ADMIN,
            AttendanceStatus.PRESENT, AttendanceSource.ADMIN_WEB, now, now
        );
        attendanceRepository.save(original);

        assertSqlState("23505", () -> attendanceRepository.save(attendance(
            fixture, fixture.enrollmentId(), null, AttendanceMethod.ADMIN,
            AttendanceStatus.LATE, AttendanceSource.ADMIN_WEB,
            now.plusSeconds(1), now.plusSeconds(1)
        )));
    }

    private Attendance attendance(
        Fixture fixture,
        UUID enrollmentId,
        UUID qrTokenId,
        AttendanceMethod method,
        AttendanceStatus status,
        AttendanceSource source,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new Attendance(
            UUID.randomUUID(),
            fixture.courseId(),
            fixture.sessionId(),
            enrollmentId,
            qrTokenId,
            method,
            status,
            createdAt,
            source,
            fixture.userId(),
            createdAt,
            updatedAt
        );
    }

    private Fixture fixture() {
        UUID institutionId = insertInstitution();
        UUID userId = insertUser();
        UUID adminUserId = insertUser();
        UUID courseId = insertCourse(institutionId);
        UUID sessionId = insertSession(courseId);
        UUID enrollmentId = insertEnrollment(courseId, userId);
        UUID adminEnrollmentId = insertEnrollment(courseId, adminUserId);
        return new Fixture(
            institutionId, userId, courseId, sessionId, enrollmentId, adminEnrollmentId);
    }

    private UUID insertInstitution() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            id, uniqueCode("att-i"), uniqueCode("att-n"));
        return id;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        String code = uniqueCode("att-u");
        jdbcTemplate.update(
            "INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            id, code, code + "@example.test");
        return id;
    }

    private UUID insertCourse(UUID institutionId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_courses (
                    id, institution_id, course_code, title, education_type,
                    start_date, end_date, capacity
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            id, institutionId, uniqueCode("att-c"),
            uniqueCode("att-ct"), "OFFLINE",
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 20);
        return id;
    }

    private UUID insertSession(UUID courseId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_course_sessions (
                    id, course_id, session_no, starts_at, ends_at, status
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            id, courseId, 1,
            OffsetDateTime.parse("2026-08-05T08:00:00Z"),
            OffsetDateTime.parse("2026-08-05T09:00:00Z"), "OPEN");
        return id;
    }

    private UUID insertEnrollment(UUID courseId, UUID userId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_enrollments (id, course_id, user_id, status) VALUES (?, ?, ?, ?)",
            id, courseId, userId, "APPROVED");
        return id;
    }

    private record Fixture(
        UUID institutionId,
        UUID userId,
        UUID courseId,
        UUID sessionId,
        UUID enrollmentId,
        UUID adminEnrollmentId
    ) {
    }
}
