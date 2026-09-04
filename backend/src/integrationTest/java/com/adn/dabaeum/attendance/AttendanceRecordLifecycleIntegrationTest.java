package com.adn.dabaeum.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.attendance.application.AttendanceApplicationService;
import com.adn.dabaeum.attendance.application.RecordAttendanceCommand;
import com.adn.dabaeum.attendance.domain.Attendance;
import com.adn.dabaeum.attendance.domain.AttendanceMethod;
import com.adn.dabaeum.attendance.domain.AttendanceQrToken;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenCodec;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenPayload;
import com.adn.dabaeum.attendance.domain.AttendanceRepository;
import com.adn.dabaeum.attendance.domain.AttendanceSource;
import com.adn.dabaeum.attendance.domain.AttendanceStatus;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.security.AuthenticatedRole;
import com.adn.dabaeum.common.security.AuthenticatedUserContext;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AttendanceRecordLifecycleIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired AttendanceApplicationService attendanceService;
    @Autowired AttendanceQrTokenCodec tokenCodec;
    @Autowired com.adn.dabaeum.attendance.domain.AttendanceQrTokenRepository tokenRepository;
    @Autowired AttendanceRepository attendanceRepository;

    @Test
    void oneDisplayedTokenCanRecordTwoApprovedEnrollmentsButDuplicateIsConflict() {
        Fixture fixture = fixture();
        Instant issuedAt = Instant.now().minusSeconds(1);
        Instant expiresAt = issuedAt.plusSeconds(600);
        UUID tokenId = UUID.randomUUID();
        byte[] nonceBytes = new byte[32];
        new SecureRandom().nextBytes(nonceBytes);
        AttendanceQrTokenPayload payload = new AttendanceQrTokenPayload(
            1, tokenId, fixture.sessionId(), issuedAt, expiresAt,
            Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes));
        String token = tokenCodec.encode(payload);
        tokenRepository.save(new AttendanceQrToken(
            tokenId, fixture.sessionId(), tokenCodec.sha256(token), fixture.issuerId(),
            issuedAt, expiresAt, null, issuedAt));

        Attendance first = attendanceService.record(
            command(fixture.sessionId(), fixture.firstEnrollmentId(), token),
            context(fixture.firstUserId(), fixture.institutionId()));
        Attendance second = attendanceService.record(
            command(fixture.sessionId(), fixture.secondEnrollmentId(), token),
            context(fixture.secondUserId(), fixture.institutionId()));

        assertThat(attendanceRepository.findById(first.id())).contains(first);
        assertThat(attendanceRepository.findById(second.id())).contains(second);
        assertThatThrownBy(() -> attendanceService.record(
            command(fixture.sessionId(), fixture.firstEnrollmentId(), token),
            context(fixture.firstUserId(), fixture.institutionId())))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ApiErrorCode.ATTENDANCE_CONFLICT);
    }

    private RecordAttendanceCommand command(UUID sessionId, UUID enrollmentId, String token) {
        return new RecordAttendanceCommand(
            sessionId,
            enrollmentId,
            AttendanceMethod.QR,
            AttendanceStatus.PRESENT,
            null,
            AttendanceSource.APP,
            token
        );
    }

    private AuthenticatedUserContext context(UUID userId, UUID institutionId) {
        return new AuthenticatedUserContext(userId, "LOCAL",
            Set.of(new AuthenticatedRole("LEARNER", institutionId)));
    }

    private Fixture fixture() {
        UUID institutionId = insertInstitution();
        UUID issuerId = insertUser();
        UUID firstUserId = insertUser();
        UUID secondUserId = insertUser();
        UUID courseId = insertCourse(institutionId);
        UUID sessionId = insertSession(courseId);
        UUID firstEnrollmentId = insertEnrollment(courseId, firstUserId);
        UUID secondEnrollmentId = insertEnrollment(courseId, secondUserId);
        return new Fixture(institutionId, issuerId, firstUserId, secondUserId, courseId,
            sessionId, firstEnrollmentId, secondEnrollmentId);
    }

    private UUID insertInstitution() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            id, uniqueCode("li"), uniqueCode("ln"));
        return id;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        String code = uniqueCode("lu");
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
            id, institutionId, uniqueCode("lc"), uniqueCode("lt"),
            "OFFLINE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 20);
        return id;
    }

    private UUID insertSession(UUID courseId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_course_sessions (
                    id, course_id, session_no, starts_at, ends_at,
                    attendance_opens_at, attendance_closes_at, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            id, courseId, 1,
            OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().plusHours(1),
            OffsetDateTime.now().minusMinutes(5), OffsetDateTime.now().plusHours(1), "OPEN");
        return id;
    }

    private UUID insertEnrollment(UUID courseId, UUID userId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO tb_enrollments (id, course_id, user_id, status, approved_at)
                VALUES (?, ?, ?, ?, ?)
                """,
            id, courseId, userId, "APPROVED", OffsetDateTime.now());
        return id;
    }

    private record Fixture(
        UUID institutionId,
        UUID issuerId,
        UUID firstUserId,
        UUID secondUserId,
        UUID courseId,
        UUID sessionId,
        UUID firstEnrollmentId,
        UUID secondEnrollmentId
    ) {
    }
}
