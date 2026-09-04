package com.adn.dabaeum.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.attendance.domain.AttendanceQrToken;
import com.adn.dabaeum.attendance.domain.AttendanceQrTokenRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AttendanceQrTokenMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    AttendanceQrTokenRepository tokenRepository;

    @Test
    void insertsFindsAndRevokesByHashWithoutPersistingRawToken() {
        UUID institutionId = insertInstitution();
        UUID issuerId = insertUser();
        UUID courseId = insertCourse(institutionId);
        UUID sessionId = insertSession(courseId);
        Instant issuedAt = Instant.parse("2026-08-05T00:00:00Z");
        String hash = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
        AttendanceQrToken token = new AttendanceQrToken(
            UUID.randomUUID(),
            sessionId,
            hash,
            issuerId,
            issuedAt,
            issuedAt.plusSeconds(30),
            null,
            issuedAt
        );

        tokenRepository.save(token);

        assertThat(tokenRepository.findByHash(hash)).contains(token);

        Instant revokedAt = issuedAt.plusSeconds(5);
        tokenRepository.revokeActiveBySession(sessionId, revokedAt);

        assertThat(tokenRepository.findByHash(hash)).hasValueSatisfying(found ->
            assertThat(found.revokedAt()).isEqualTo(revokedAt));
    }

    private UUID insertInstitution() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            id, uniqueCode("qr-inst"), uniqueCode("qr-institution-name"));
        return id;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        String code = uniqueCode("qr-user");
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
            id, institutionId, uniqueCode("qr-course"), uniqueCode("qr-course-title"),
            "OFFLINE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 20);
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
            OffsetDateTime.parse("2026-08-05T09:00:00Z"),
            "OPEN");
        return id;
    }
}
