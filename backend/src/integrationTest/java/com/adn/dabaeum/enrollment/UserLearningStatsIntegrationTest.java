package com.adn.dabaeum.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.enrollment.application.EnrollmentQueryService;
import com.adn.dabaeum.enrollment.domain.UserLearningStats;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * B-4 학습 통계 SQL 이 실제 Postgres 에서 맞는 값을 내는지 본다.
 *
 * <p>확인할 것은 둘이다 — 취소된 회차를 분모에서 빼는지, 출결이 없는 수강을 NULL 로 흘려보내지
 * 않고 0 으로 평균에 넣는지. 둘 중 하나만 틀려도 회원 상세의 진도율이 조용히 부풀어난다.
 * 공유 DB 라 이 테스트가 만든 사용자에 한정된 값만 단언한다.
 */
class UserLearningStatsIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired
    private EnrollmentQueryService enrollmentQueryService;

    @Test
    void 평균_진도율은_취소_회차를_빼고_출결_없는_수강을_0_으로_센다() {
        UUID institutionId = insertInstitution();
        UUID userId = insertUser();

        // 회차 4개 중 1개는 취소 — 분모는 3이어야 한다.
        UUID attendedCourseId = insertCourse(institutionId);
        UUID attendedSessionId = insertSession(attendedCourseId, 1, "SCHEDULED");
        insertSession(attendedCourseId, 2, "SCHEDULED");
        insertSession(attendedCourseId, 3, "SCHEDULED");
        insertSession(attendedCourseId, 4, "CANCELLED");

        UUID emptyCourseId = insertCourse(institutionId);
        insertSession(emptyCourseId, 1, "SCHEDULED");
        insertSession(emptyCourseId, 2, "SCHEDULED");
        insertSession(emptyCourseId, 3, "SCHEDULED");
        insertSession(emptyCourseId, 4, "CANCELLED");

        // 같은 (강좌, 사용자) 로는 활성 수강이 하나뿐이라(uq_tb_enrollments_active)
        // 승인 수강 두 건은 강좌를 나눠 만든다.
        UUID attendedEnrollmentId = insertEnrollment(attendedCourseId, userId);
        UUID emptyEnrollmentId = insertEnrollment(emptyCourseId, userId);

        // 살아 있는 회차 3개 중 1개 출석 → 33%, 나머지 수강은 출결이 없어 0%.
        insertAttendance(attendedCourseId, attendedSessionId, attendedEnrollmentId);
        insertCompletion(attendedEnrollmentId, 120);
        insertCompletion(emptyEnrollmentId, 30);

        UserLearningStats stats = enrollmentQueryService.learningStatsOf(userId);

        assertThat(stats.averageProgressPercent()).isEqualTo(17);
        assertThat(stats.totalStudyMinutes()).isEqualTo(150);
        assertThat(stats.totalCount()).isEqualTo(2);
        assertThat(stats.inquiryCount()).isZero();
    }

    private UUID insertInstitution() {
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, uniqueCode("institution"), uniqueCode("institution-name"));
        return institutionId;
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        String userCode = uniqueCode("user");
        jdbcTemplate.update(
            "INSERT INTO tb_users (id, name, email) VALUES (?, ?, ?)",
            userId, userCode, userCode + "@example.test");
        return userId;
    }

    private UUID insertCourse(UUID institutionId) {
        UUID courseId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (
                id, institution_id, course_code, title, education_type,
                start_date, end_date, capacity
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            courseId, institutionId, uniqueCode("course"), uniqueCode("course-title"),
            "OFFLINE", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 20);
        return courseId;
    }

    /** 타임스탬프는 OffsetDateTime 으로 바인딩한다 — Instant 는 드라이버가 받지 않는다. */
    private UUID insertSession(UUID courseId, int sessionNo, String status) {
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime startsAt = OffsetDateTime.parse("2026-08-01T09:00:00+09:00")
            .plusDays(sessionNo);
        jdbcTemplate.update("""
            INSERT INTO tb_course_sessions (
                id, course_id, session_no, starts_at, ends_at, status
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            sessionId, courseId, sessionNo, startsAt, startsAt.plusHours(1), status);
        return sessionId;
    }

    private UUID insertEnrollment(UUID courseId, UUID userId) {
        UUID enrollmentId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO tb_enrollments (id, course_id, user_id, status) VALUES (?, ?, ?, ?)",
            enrollmentId, courseId, userId, "APPROVED");
        return enrollmentId;
    }

    private void insertAttendance(UUID courseId, UUID sessionId, UUID enrollmentId) {
        jdbcTemplate.update("""
            INSERT INTO tb_attendance_records (
                id, course_id, session_id, enrollment_id, attendance_method, status, source
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(), courseId, sessionId, enrollmentId,
            "ADMIN", "PRESENT", "ADMIN_WEB");
    }

    private void insertCompletion(UUID enrollmentId, int completedMinutes) {
        jdbcTemplate.update("""
            INSERT INTO tb_completions (id, enrollment_id, completed_minutes)
            VALUES (?, ?, ?)
            """,
            UUID.randomUUID(), enrollmentId, completedMinutes);
    }
}
