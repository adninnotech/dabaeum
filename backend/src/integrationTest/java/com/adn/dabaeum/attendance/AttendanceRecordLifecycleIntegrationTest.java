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
    void managerRecordsTwoApprovedEnrollmentsButDuplicateIsConflict() {
        Fixture fixture = fixture();
        AuthenticatedUserContext manager = manager(fixture.issuerId(), fixture.institutionId());

        Attendance first = attendanceService.record(
            adminCommand(fixture.sessionId(), fixture.firstEnrollmentId()), manager);
        Attendance second = attendanceService.record(
            adminCommand(fixture.sessionId(), fixture.secondEnrollmentId()), manager);

        assertThat(attendanceRepository.findById(first.id())).contains(first);
        assertThat(attendanceRepository.findById(second.id())).contains(second);
        assertThatThrownBy(() -> attendanceService.record(
            adminCommand(fixture.sessionId(), fixture.firstEnrollmentId()), manager))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ApiErrorCode.ATTENDANCE_CONFLICT);
    }

    /** 일반 QR 은 닫혔다. 유효한 토큰을 가진 본인이어도 기록하지 않는다. */
    @Test
    void learnerQrRecordingIsRejectedEvenWithAValidToken() {
        Fixture fixture = fixture();
        Instant issuedAt = Instant.now().minusSeconds(1);
        Instant expiresAt = issuedAt.plusSeconds(600);
        UUID tokenId = UUID.randomUUID();
        byte[] nonceBytes = new byte[32];
        new SecureRandom().nextBytes(nonceBytes);
        String token = tokenCodec.encode(new AttendanceQrTokenPayload(
            1, tokenId, fixture.sessionId(), issuedAt, expiresAt,
            Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes)));
        tokenRepository.save(new AttendanceQrToken(
            tokenId, fixture.sessionId(), tokenCodec.sha256(token), fixture.issuerId(),
            issuedAt, expiresAt, null, issuedAt));

        assertThatThrownBy(() -> attendanceService.record(new RecordAttendanceCommand(
                fixture.sessionId(), fixture.firstEnrollmentId(), AttendanceMethod.QR,
                AttendanceStatus.PRESENT, null, AttendanceSource.APP, token),
            context(fixture.firstUserId(), fixture.institutionId())))
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(ApiErrorCode.ATTENDANCE_METHOD_NOT_SUPPORTED);
        assertThat(attendanceRepository.findBySessionAndEnrollment(
            fixture.sessionId(), fixture.firstEnrollmentId())).isEmpty();
    }

    private RecordAttendanceCommand adminCommand(UUID sessionId, UUID enrollmentId) {
        return new RecordAttendanceCommand(sessionId, enrollmentId, AttendanceMethod.ADMIN,
            AttendanceStatus.PRESENT, null, AttendanceSource.ADMIN_WEB, null);
    }

    private AuthenticatedUserContext manager(UUID userId, UUID institutionId) {
        return new AuthenticatedUserContext(userId, "LOCAL",
            Set.of(new AuthenticatedRole("INSTITUTION_ADMIN", institutionId)));
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
