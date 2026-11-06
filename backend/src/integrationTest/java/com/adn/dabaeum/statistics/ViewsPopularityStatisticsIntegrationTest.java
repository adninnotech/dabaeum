package com.adn.dabaeum.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.dashboard.domain.DashboardRepository;
import com.adn.dabaeum.dashboard.domain.InstitutionDashboard;
import com.adn.dabaeum.popularity.domain.PopularCourseScore;
import com.adn.dabaeum.popularity.domain.PopularityRepository;
import com.adn.dabaeum.popularity.domain.PopularitySegment;
import com.adn.dabaeum.statistics.domain.BadgeStatGroup;
import com.adn.dabaeum.statistics.domain.EnrollmentStatGroup;
import com.adn.dabaeum.statistics.domain.EnrollmentStatRow;
import com.adn.dabaeum.statistics.domain.StatisticsRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import com.adn.dabaeum.view.application.CourseViewService;
import com.adn.dabaeum.view.domain.ViewSaltRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 설계서 §8·§9 — V44 조회 로그·salt·신청 구군 스냅샷·인기 재계산·통계 SQL. */
class ViewsPopularityStatisticsIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired CourseViewService views;
    @Autowired ViewSaltRepository salts;
    @Autowired PopularityRepository popularity;
    @Autowired StatisticsRepository statistics;
    @Autowired DashboardRepository dashboards;

    @Test
    void sameViewerCountsOncePerDayAndAnonymousKeepsNoUser() {
        UUID course = insertCourse("RECRUITING", "TSIX_040201");
        UUID user = insertUser(LocalDate.of(1995, 5, 5));

        views.record(course, user, "203.0.113.7", "junit");
        views.record(course, user, "203.0.113.7", "junit");
        views.record(course, null, "203.0.113.7", "junit");
        views.record(course, null, "203.0.113.7", "junit");

        assertThat(count("SELECT COUNT(*) FROM tb_course_views WHERE course_id = ?", course)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM tb_course_views WHERE course_id = ? AND user_id IS NULL", course)).isEqualTo(1);
    }

    @Test
    void saltIsOneRowPerDayAndStable() {
        LocalDate day = LocalDate.of(2031, 1, 1);
        byte[] first = salts.saltFor(day);
        byte[] second = salts.saltFor(day);

        assertThat(second).isEqualTo(first).hasSize(32);
        assertThat(count("SELECT COUNT(*) FROM tb_view_salts WHERE view_date = DATE '2031-01-01'")).isEqualTo(1);
    }

    @Test
    void popularityRecalculationScoresSegmentsAndSkipsDraft() {
        UUID open = insertCourse("RECRUITING", "TSIX_040201");
        UUID draft = insertCourse("DRAFT", "TSIX_040201");
        UUID user = insertUser(LocalDate.of(1995, 5, 5));
        enroll(open, user, "APPLIED");
        enroll(draft, user, "APPLIED");
        views.record(open, user, "203.0.113.7", "junit");
        views.record(open, null, "198.51.100.1", "junit");

        popularity.recalculate(30, 3, 1, Instant.now());

        PopularCourseScore all = find(popularity.top(PopularitySegment.ALL, "*", 30, 500), open);
        assertThat(all.enrollmentCount()).isEqualTo(1);
        assertThat(all.viewCount()).isEqualTo(2);
        assertThat(all.score()).isEqualByComparingTo("5");
        assertThat(find(popularity.top(PopularitySegment.AGE_BAND, "30", 30, 500), open)).isNotNull();
        assertThat(popularity.top(PopularitySegment.ALL, "*", 30, 500)).noneMatch(score -> score.courseId().equals(draft));
    }

    @Test
    void enrollmentStatisticsRunForEveryAxis() {
        UUID institution = insertInstitution();
        UUID course = insertCourse(institution, "RECRUITING", "TSIX_040201");
        UUID user = insertUser(LocalDate.of(1988, 3, 3));
        UUID enrollment = enroll(course, user, "APPROVED");
        jdbcTemplate.update("UPDATE tb_enrollments SET approved_at = now() WHERE id = ?", enrollment);
        jdbcTemplate.update("""
            INSERT INTO tb_completions (id, enrollment_id, status, completed_at, confirmed_at)
            VALUES (?, ?, 'COMPLETED', now(), now())
            """, UUID.randomUUID(), enrollment);
        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);

        for (EnrollmentStatGroup group : EnrollmentStatGroup.values()) {
            List<EnrollmentStatRow> rows = statistics.enrollments(group, from, to, "TSIX_04", institution, 20);
            assertThat(rows).as(group.name()).singleElement().satisfies(row -> {
                assertThat(row.applied()).isEqualTo(1);
                assertThat(row.approved()).isEqualTo(1);
                assertThat(row.completed()).isEqualTo(1);
            });
        }
        assertThat(statistics.enrollments(EnrollmentStatGroup.AGE_BAND, from, to, null, institution, 20))
            .extracting(EnrollmentStatRow::key).containsExactly("30");
        assertThat(statistics.enrollments(EnrollmentStatGroup.GENDER, from, to, null, institution, 20))
            .extracting(EnrollmentStatRow::key).containsExactly("UNKNOWN");
        assertThat(statistics.enrollments(EnrollmentStatGroup.CONTENT, from, to, null, institution, 20))
            .extracting(EnrollmentStatRow::label).containsExactly("통계 강좌");
    }

    /** QA DATA-01: 승인 뒤 철회된 수강은 신청에는 남고 승인 수에서는 빠진다. 기관 대시보드도 지금 상태로 센다. */
    @Test
    void withdrawnEnrollmentLeavesApprovedCountsButStaysApplied() {
        UUID institution = insertInstitution();
        UUID course = insertCourse(institution, "RECRUITING", "TSIX_040201");
        UUID approved = enroll(course, insertUser(LocalDate.of(1988, 3, 3)), "APPROVED");
        UUID withdrawn = enroll(course, insertUser(LocalDate.of(1990, 1, 1)), "WITHDRAWN");
        jdbcTemplate.update("UPDATE tb_enrollments SET approved_at = now() WHERE id IN (?, ?)", approved, withdrawn);
        jdbcTemplate.update("UPDATE tb_enrollments SET withdrawn_at = now() WHERE id = ?", withdrawn);
        jdbcTemplate.update("""
            INSERT INTO tb_completions (id, enrollment_id, status, completed_at, confirmed_at)
            VALUES (?, ?, 'COMPLETED', now(), now())
            """, UUID.randomUUID(), approved);
        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);

        assertThat(statistics.enrollments(EnrollmentStatGroup.YEAR, from, to, null, institution, 20))
            .singleElement().satisfies(row -> {
                assertThat(row.applied()).isEqualTo(2);
                assertThat(row.approved()).isEqualTo(1);
                assertThat(row.completed()).isEqualTo(1);
            });
        InstitutionDashboard dashboard = dashboards.institutionDashboard(institution);
        assertThat(dashboard.enrollmentCount()).isEqualTo(2);
        assertThat(dashboard.approvedEnrollmentCount()).isEqualTo(1);
        assertThat(dashboard.completionCount()).isEqualTo(1);
    }

    @Test
    void badgeStatisticsRunForEveryAxis() {
        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);
        for (BadgeStatGroup group : BadgeStatGroup.values()) {
            assertThat(statistics.badges(group, from, to)).as(group.name()).isNotNull();
        }
    }

    private static PopularCourseScore find(List<PopularCourseScore> scores, UUID course) {
        return scores.stream().filter(score -> score.courseId().equals(course)).findFirst().orElseThrow();
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private UUID insertInstitution() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            id, "sta" + id.toString().substring(0, 8), "통계 기관");
        return id;
    }

    private UUID insertCourse(String status, String classification) {
        return insertCourse(insertInstitution(), status, classification);
    }

    private UUID insertCourse(UUID institution, String status, String classification) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (id, institution_id, course_code, title, education_type, start_date, end_date,
                                    capacity, status, classification_code)
            VALUES (?, ?, ?, '통계 강좌', 'OFFLINE', DATE '2026-10-01', DATE '2026-10-31', 10, ?, ?)
            """, id, institution, "STA-" + id.toString().substring(0, 8), status, classification);
        return id;
    }

    private UUID insertUser(LocalDate birthDate) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name, birth_date) VALUES (?, ?, ?)",
            id, "통계 학습자", birthDate);
        return id;
    }

    private UUID enroll(UUID course, UUID user, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_enrollments (id, course_id, user_id, status) VALUES (?, ?, ?, ?)",
            id, course, user, status);
        return id;
    }
}
