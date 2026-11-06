package com.adn.dabaeum.usage;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * docs/specs/usage-statistics.md §12 — 시연용 더미 SQL(scripts/usage-demo). 테스트 트랜잭션 안에서 돌리고 롤백한다.
 * 1년치 미리보기는 USAGE_DEMO_PREVIEW=true 일 때만 돈다(행이 40만 개쯤이라 평소에는 끈다).
 */
class UsageDemoDataScriptIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Path GENERATE = Path.of("scripts/usage-demo/generate.sql");
    private static final Path REMOVE = Path.of("scripts/usage-demo/remove.sql");
    private static final Path RENAME = Path.of("scripts/usage-demo/rename-names.sql");
    private static final Path RESTORE = Path.of("scripts/usage-demo/restore-names.sql");
    private static final Path HIDE_QA = Path.of("scripts/usage-demo/hide-qa-courses.sql");
    private static final Path SHOW_QA = Path.of("scripts/usage-demo/show-qa-courses.sql");
    private static final Path DOWNSIZE = Path.of("scripts/usage-demo/downsize.sql");
    private static final Path RESTORE_DOWNSIZE = Path.of("scripts/usage-demo/restore-downsize.sql");
    private static final String DEMO_ACCOUNTS =
        "('syn-00617@synthetic.invalid', 'syn-01091@synthetic.invalid', 'syn-01775@synthetic.invalid')";

    @Test
    void oneWeekLooksLikeOperationAndRerunReplacesInsteadOfDoubling() throws IOException {
        generate("2026-10-19", "2026-10-25");
        List<Map<String, Object>> first = visitorsPerDay("2026-10-19", "2026-10-25");

        assertThat(first).hasSize(7);
        double weekday = average(first.subList(0, 5));
        double weekend = average(first.subList(5, 7));
        assertThat(weekday).isBetween(120.0, 450.0);
        assertThat(weekend).isLessThan(weekday);
        assertThat(count("SELECT COUNT(*) FROM tb_usage_events WHERE viewer_key LIKE 'demo%' AND event_type = 'SEARCH'"))
            .isPositive();
        assertThat(count("SELECT COUNT(*) FROM tb_course_views WHERE viewer_key LIKE 'demo%'")).isPositive();
        assertThat(count("""
            SELECT COUNT(*) FROM tb_usage_events
             WHERE viewer_key LIKE 'demo%' AND (length(viewer_key) <> 64 OR viewer_key !~ '^demo[0-9a-f]{60}$')
            """)).isZero();
        assertThat(count("SELECT COUNT(*) FROM tb_usage_aggregated_days WHERE stat_date >= DATE '2026-10-19'")).isZero();

        generate("2026-10-19", "2026-10-25");

        int rerun = visitorsPerDay("2026-10-19", "2026-10-25").stream().mapToInt(row -> ((Number) row.get("visitors")).intValue()).sum();
        int firstTotal = first.stream().mapToInt(row -> ((Number) row.get("visitors")).intValue()).sum();
        assertThat(rerun).isBetween(firstTotal / 2, firstTotal * 3 / 2);
    }

    @Test
    void nothingBeforeLaunchAndOpeningWeeksRampUp() throws IOException {
        jdbcTemplate.update("""
            INSERT INTO tb_usage_daily_stats (stat_date, metric, target_key, member_type, gender, age_band, count)
            VALUES (DATE '2026-07-01', 'ENROLLMENT', '', 'MEMBER', 'FEMALE', '30', 5) ON CONFLICT DO NOTHING
            """);
        run(RESTORE);
        run(RENAME);

        generate("2026-08-01", "2026-10-23");

        assertThat(count("SELECT COUNT(*) FROM tb_usage_events WHERE viewer_key LIKE 'demo%' AND event_date < DATE '2026-08-17'"))
            .isZero();
        assertThat(count("SELECT COUNT(*) FROM tb_usage_daily_stats WHERE stat_date < DATE '2026-08-17'")).isZero();
        double openingWeek = weekdayAverage("2026-08-17", "2026-08-21");
        double lateOctober = weekdayAverage("2026-10-19", "2026-10-23");
        assertThat(openingWeek).isLessThan(lateOctober * 0.7);
        assertThat(count("""
            SELECT COUNT(*) FROM (
              SELECT g.d::date AS d,
                     (SELECT COUNT(*) FROM tb_usage_events WHERE viewer_key LIKE 'demo%' AND event_type = 'VISIT'
                         AND event_date = g.d::date) AS visitors,
                     (SELECT COUNT(*) FROM tb_enrollments
                       WHERE (applied_at AT TIME ZONE 'Asia/Seoul')::date = g.d::date) AS enrollments
                FROM generate_series(DATE '2026-08-17', DATE '2026-10-23', INTERVAL '1 day') AS g(d)) x
             WHERE visitors < enrollments
            """)).as("days where enrollments exceed demo visitors").isZero();
        assertThat(count("""
            SELECT COUNT(*) FROM tb_usage_events
             WHERE viewer_key LIKE 'demo%' AND event_type = 'VISIT' AND member_type = 'MEMBER' AND age_band IN ('40', '50')
            """)).isGreaterThan(2 * count("""
            SELECT COUNT(*) FROM tb_usage_events
             WHERE viewer_key LIKE 'demo%' AND event_type = 'VISIT' AND member_type = 'MEMBER' AND age_band = '20'
            """));
    }

    @Test
    void removeDeletesOnlyDemoRows() throws IOException {
        jdbcTemplate.update("""
            INSERT INTO tb_usage_events (event_date, event_type, viewer_key, member_type, gender, age_band)
            VALUES (DATE '2026-10-20', 'VISIT', ?, 'GUEST', 'UNKNOWN', 'UNKNOWN')
            """, "a".repeat(64));
        generate("2026-10-20", "2026-10-21");

        jdbcTemplate.execute(Files.readString(REMOVE, StandardCharsets.UTF_8));

        assertThat(count("SELECT COUNT(*) FROM tb_usage_events WHERE viewer_key LIKE 'demo%'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM tb_course_views WHERE viewer_key LIKE 'demo%'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM tb_usage_events WHERE viewer_key = '" + "a".repeat(64) + "'")).isEqualTo(1);
    }

    @Test
    void renameGivesSyntheticCoursesRealisticNamesAndRestoreBringsThemBack() throws IOException {
        // dev 에 이미 바꾼 이름이 있을 수 있다. 먼저 원래 이름으로 돌려 놓고 시작한다(모두 롤백된다).
        run(RESTORE);
        Integer synthetic = count("SELECT COUNT(*) FROM tb_courses WHERE course_code LIKE 'SYN-%'");
        String before = jdbcTemplate.queryForObject(
            "SELECT string_agg(title, '|' ORDER BY id) FROM tb_courses WHERE course_code LIKE 'SYN-%'", String.class);
        String namesBefore = jdbcTemplate.queryForObject(
            "SELECT string_agg(name, '|' ORDER BY id) FROM tb_users WHERE email LIKE '%@synthetic.invalid'", String.class);
        String profileBefore = jdbcTemplate.queryForObject(
            "SELECT string_agg(COALESCE(birth_date::text, '-') || COALESCE(gender, '-'), '|' ORDER BY id) FROM tb_users"
                + " WHERE email LIKE '%@synthetic.invalid'", String.class);
        String demoAccountsBefore = jdbcTemplate.queryForObject(
            "SELECT string_agg(birth_date || gender, '|' ORDER BY email) FROM tb_users WHERE email IN"
                + " ('syn-00617@synthetic.invalid', 'syn-01091@synthetic.invalid', 'syn-01775@synthetic.invalid')", String.class);
        String signupsBefore = jdbcTemplate.queryForObject(
            "SELECT string_agg(created_at::text, '|' ORDER BY id) FROM tb_users WHERE email LIKE '%@synthetic.invalid'",
            String.class);

        run(RENAME);
        run(RENAME);

        assertThat(count("SELECT COUNT(*) FROM tb_courses WHERE course_code LIKE 'SYN-%' AND title LIKE '가상강좌%'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM tb_institutions WHERE institution_code LIKE 'SYN-%' AND name LIKE '가상기관%'"))
            .isZero();
        assertThat(count("SELECT COUNT(*) FROM tb_courses WHERE course_code NOT LIKE 'SYN-%' AND title LIKE '가상강좌%'"))
            .isZero();
        assertThat(count("SELECT COUNT(*) FROM demo_name_backup WHERE kind = 'COURSE'")).isEqualTo(synthetic);
        assertThat(count("""
            SELECT COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid'
               AND (name LIKE '가상회원%' OR char_length(name) NOT BETWEEN 2 AND 5)
            """)).isZero();
        assertThat(count("""
            SELECT COUNT(DISTINCT name) FROM tb_users WHERE email LIKE '%@synthetic.invalid'
            """)).isGreaterThan(300);
        String middleAged = "SELECT COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid'"
            + " AND EXTRACT(YEAR FROM age(birth_date)) BETWEEN 40 AND 59";
        String twenties = "SELECT COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid'"
            + " AND EXTRACT(YEAR FROM age(birth_date)) BETWEEN 20 AND 29";
        assertThat(count(middleAged)).isGreaterThan(4 * count(twenties));
        double female = jdbcTemplate.queryForObject("""
            SELECT AVG(CASE WHEN gender = 'FEMALE' THEN 1.0 ELSE 0 END) FROM tb_users WHERE email LIKE '%@synthetic.invalid'
            """, Double.class);
        assertThat(female).isBetween(0.50, 0.62);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT string_agg(birth_date || gender, '|' ORDER BY email) FROM tb_users WHERE email IN"
                + " ('syn-00617@synthetic.invalid', 'syn-01091@synthetic.invalid', 'syn-01775@synthetic.invalid')",
            String.class)).isEqualTo(demoAccountsBefore);
        assertThat(count("""
            SELECT COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid'
               AND (created_at < TIMESTAMPTZ '2026-08-17 00:00+09' OR created_at >= TIMESTAMPTZ '2026-10-30 00:00+09')
            """)).isZero();
        assertThat(count("""
            SELECT COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid'
               AND created_at < TIMESTAMPTZ '2026-08-24 00:00+09'
            """)).isGreaterThan(count("""
            SELECT COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid'
               AND created_at >= TIMESTAMPTZ '2026-10-23 00:00+09'
            """));

        run(RESTORE);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT string_agg(title, '|' ORDER BY id) FROM tb_courses WHERE course_code LIKE 'SYN-%'", String.class))
            .isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT string_agg(created_at::text, '|' ORDER BY id) FROM tb_users WHERE email LIKE '%@synthetic.invalid'",
            String.class)).isEqualTo(signupsBefore);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT string_agg(name, '|' ORDER BY id) FROM tb_users WHERE email LIKE '%@synthetic.invalid'", String.class))
            .isEqualTo(namesBefore);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT string_agg(COALESCE(birth_date::text, '-') || COALESCE(gender, '-'), '|' ORDER BY id) FROM tb_users"
                + " WHERE email LIKE '%@synthetic.invalid'", String.class)).isEqualTo(profileBefore);
        assertThat(count("SELECT COUNT(*) FROM pg_tables WHERE tablename = 'demo_name_backup'")).isZero();
    }

    @Test
    void downsizeLooksLikeTwoAndAHalfMonthsAndRestoreBringsEverythingBack() throws IOException {
        // dev 에 이미 줄인 상태가 있을 수 있다. 먼저 되돌려 놓고 시작한다(모두 롤백된다).
        run(RESTORE_DOWNSIZE);
        String snapshot = """
            SELECT md5(string_agg(x, '|' ORDER BY x)) FROM (
              SELECT 'u' || id || name FROM tb_users WHERE email LIKE '%@synthetic.invalid'
              UNION ALL SELECT 'e' || id || status || COALESCE(approved_at::text, '') FROM tb_enrollments
              UNION ALL SELECT 'c' || id || capacity || institution_id || COALESCE(deleted_at::text, '') FROM tb_courses
              UNION ALL SELECT 'p' || id FROM tb_completions
              UNION ALL SELECT 'i' || id FROM tb_course_interests
              UNION ALL SELECT 'r' || id FROM tb_user_roles
              UNION ALL SELECT 'x' || user_id || classification_code FROM tb_user_interests
              UNION ALL SELECT 'n' || id || COALESCE(deleted_at::text, '') FROM tb_institutions) t(x)
            """;
        String before = jdbcTemplate.queryForObject(snapshot, String.class);
        int demoEnrollments = count("SELECT COUNT(*) FROM tb_enrollments e JOIN tb_users u ON u.id = e.user_id"
            + " WHERE u.email IN " + DEMO_ACCOUNTS);
        int realEnrollments = count("SELECT COUNT(*) FROM tb_enrollments e JOIN tb_users u ON u.id = e.user_id"
            + " WHERE u.email NOT LIKE '%@synthetic.invalid'");

        run(DOWNSIZE);
        run(DOWNSIZE);

        assertThat(count("SELECT COUNT(*) FROM tb_institutions WHERE institution_code LIKE 'SYN-%' AND deleted_at IS NULL"))
            .isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM tb_courses WHERE course_code LIKE 'SYN-%' AND deleted_at IS NULL"))
            .isBetween(60, 70);
        assertThat(count("SELECT COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid'")).isEqualTo(650);
        assertThat(count("SELECT COUNT(*) FROM tb_enrollments e JOIN tb_users u ON u.id = e.user_id"
            + " WHERE u.email LIKE '%@synthetic.invalid'")).isBetween(600, 1100);
        assertThat(count("SELECT COUNT(*) FROM tb_enrollments e JOIN tb_users u ON u.id = e.user_id"
            + " WHERE u.email IN " + DEMO_ACCOUNTS)).isEqualTo(demoEnrollments);
        assertThat(count("SELECT COUNT(*) FROM tb_enrollments e JOIN tb_users u ON u.id = e.user_id"
            + " WHERE u.email NOT LIKE '%@synthetic.invalid'")).isEqualTo(realEnrollments);
        assertThat(count("""
            SELECT COUNT(*) FROM tb_courses c WHERE c.course_code LIKE 'SYN-%' AND c.deleted_at IS NULL
               AND (c.capacity NOT IN (15, 20)
                    OR (SELECT COUNT(*) FROM tb_enrollments e WHERE e.course_id = c.id AND e.status = 'APPROVED') > c.capacity)
            """)).as("capacity 15/20 and approved within capacity").isZero();
        assertThat(count("""
            SELECT COUNT(*) FROM tb_courses c WHERE c.course_code LIKE 'SYN-%' AND c.deleted_at IS NULL AND c.status = 'RECRUITING'
               AND (SELECT COUNT(*) FROM tb_enrollments e WHERE e.course_id = c.id AND e.status = 'APPROVED') = 0
            """)).as("recruiting courses show applicants").isZero();
        assertThat(count("""
            SELECT COUNT(*) FROM tb_courses c JOIN tb_institutions i ON i.id = c.institution_id
             WHERE c.deleted_at IS NULL AND i.deleted_at IS NOT NULL
            """)).as("no visible course under a hidden institution").isZero();

        run(RESTORE_DOWNSIZE);

        assertThat(jdbcTemplate.queryForObject(snapshot, String.class)).isEqualTo(before);
        assertThat(count("SELECT COUNT(*) FROM pg_tables WHERE tablename LIKE 'demo_downsize%'")).isZero();
    }

    @Test
    void dailyEnrollmentsContinueAfterOctoberWithinCapacityAndCanBeRemoved() throws IOException {
        run(RENAME);
        run(DOWNSIZE);

        String dates = """
            SELECT md5(string_agg(x, '|' ORDER BY x)) FROM (
              SELECT c.id::text || c.start_date::text || c.end_date::text || COALESCE(c.recruit_end_date::text, '') FROM tb_courses c WHERE c.course_code LIKE 'SYN-%'
              UNION ALL SELECT s.id::text || s.starts_at::text || s.ends_at::text FROM tb_course_sessions s
                JOIN tb_courses c ON c.id = s.course_id WHERE c.course_code LIKE 'SYN-%') t(x)
            """;
        run(RESTORE_DOWNSIZE);
        String datesBefore = jdbcTemplate.queryForObject(dates, String.class);
        run(DOWNSIZE);

        generate("2026-10-27", "2026-11-01");
        generate("2026-10-27", "2026-11-01");

        assertThat(count("""
            SELECT COUNT(*) FROM tb_courses WHERE course_code LIKE 'SYN-%' AND deleted_at IS NULL AND status = 'RECRUITING'
               AND recruit_end_date < DATE '2026-11-14'
            """)).as("recruiting courses stay open through the inspection").isZero();

        System.out.println("== 이어 넣은 신청(날짜·건수)");
        jdbcTemplate.queryForList("SELECT added_on, COUNT(*) n FROM demo_added_enrollments GROUP BY 1 ORDER BY 1")
            .forEach(row -> System.out.println("   " + row.values()));
        System.out.println("== 모집 중 가상 강좌 모집 기간(종료 달·수)");
        jdbcTemplate.queryForList("""
            SELECT to_char(recruit_end_date, 'YYYY-MM-DD') e, to_char(start_date, 'YYYY-MM-DD') s, COUNT(*) n FROM tb_courses
             WHERE course_code LIKE 'SYN-%' AND deleted_at IS NULL AND status = 'RECRUITING' GROUP BY 1, 2 ORDER BY 1""")
            .forEach(row -> System.out.println("   " + row.values()));
        assertThat(count("SELECT COUNT(DISTINCT added_on) FROM demo_added_enrollments")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM demo_added_enrollments")).isBetween(8, 36);
        assertThat(count("""
            SELECT COUNT(*) FROM tb_courses c WHERE c.course_code LIKE 'SYN-%' AND c.deleted_at IS NULL
               AND (SELECT COUNT(*) FROM tb_enrollments e WHERE e.course_id = c.id AND e.status = 'APPROVED') > c.capacity
            """)).as("approved within capacity").isZero();
        assertThat(count("""
            SELECT COUNT(*) FROM demo_added_enrollments a JOIN tb_enrollments e ON e.id = a.id JOIN tb_courses c ON c.id = e.course_id
             WHERE a.added_on NOT BETWEEN c.recruit_start_date AND c.recruit_end_date
            """)).as("added within the recruit window").isZero();
        int added = count("SELECT COUNT(*) FROM demo_added_enrollments");
        int enrollments = count("SELECT COUNT(*) FROM tb_enrollments");

        run(REMOVE);

        assertThat(count("SELECT COUNT(*) FROM tb_enrollments")).isEqualTo(enrollments - added);
        assertThat(count("SELECT COUNT(*) FROM pg_tables WHERE tablename = 'demo_added_enrollments'")).isZero();
        run(RESTORE_DOWNSIZE);
        assertThat(jdbcTemplate.queryForObject(dates, String.class)).isEqualTo(datesBefore);
    }

    @Test
    void qaCoursesHideDuringDemoAndComeBackWithTheirStatus() throws IOException {
        String qaCourses = "FROM tb_courses WHERE course_code NOT LIKE 'SYN-%' AND title LIKE 'QA%' AND deleted_at IS NULL";
        String before = jdbcTemplate.queryForObject("SELECT string_agg(id || ':' || status, '|' ORDER BY id) " + qaCourses,
            String.class);
        Integer institutionsBefore = count("SELECT COUNT(*) FROM tb_institutions WHERE status = 'ACTIVE'");

        run(HIDE_QA);

        assertThat(count("SELECT COUNT(*) " + qaCourses + " AND status NOT IN ('DRAFT', 'CANCELLED')")).isZero();
        assertThat(count("SELECT COUNT(*) FROM tb_institutions WHERE status = 'ACTIVE'")).isEqualTo(institutionsBefore);

        run(SHOW_QA);

        assertThat(jdbcTemplate.queryForObject("SELECT string_agg(id || ':' || status, '|' ORDER BY id) " + qaCourses,
            String.class)).isEqualTo(before);
        assertThat(count("SELECT COUNT(*) FROM pg_tables WHERE tablename = 'demo_qa_course_backup'")).isZero();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "USAGE_DEMO_PREVIEW", matches = "true")
    void previewOneYear() throws IOException {
        run(RENAME);
        run(DOWNSIZE);
        print("줄인 규모(기관·강좌·회원·신청·수료)", """
            SELECT (SELECT COUNT(*) FROM tb_institutions WHERE institution_code LIKE 'SYN-%' AND deleted_at IS NULL) a,
                   (SELECT COUNT(*) FROM tb_courses WHERE course_code LIKE 'SYN-%' AND deleted_at IS NULL) b,
                   (SELECT COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid') c,
                   (SELECT COUNT(*) FROM tb_enrollments e JOIN tb_users u ON u.id = e.user_id WHERE u.email LIKE '%@synthetic.invalid') d,
                   (SELECT COUNT(*) FROM tb_completions cp JOIN tb_enrollments e ON e.id = cp.enrollment_id
                      JOIN tb_users u ON u.id = e.user_id WHERE u.email LIKE '%@synthetic.invalid' AND cp.status = 'COMPLETED') e""");
        print("상태별 강좌(수·평균 정원·평균 승인·최대 승인·평균 대기)", """
            SELECT c.status, COUNT(*) n, ROUND(AVG(c.capacity), 1) cap,
                   ROUND(AVG((SELECT COUNT(*) FROM tb_enrollments e WHERE e.course_id = c.id AND e.status = 'APPROVED')), 1) appr,
                   MAX((SELECT COUNT(*) FROM tb_enrollments e WHERE e.course_id = c.id AND e.status = 'APPROVED')) maxappr,
                   ROUND(AVG((SELECT COUNT(*) FROM tb_enrollments e WHERE e.course_id = c.id AND e.status = 'APPLIED')), 1) pend
              FROM tb_courses c WHERE c.course_code LIKE 'SYN-%' AND c.deleted_at IS NULL GROUP BY 1 ORDER BY 1""");
        print("추천 학습 표본(양성·회원·강좌)", """
            SELECT COUNT(*) pos, COUNT(DISTINCT e.user_id) users, COUNT(DISTINCT e.course_id) courses FROM tb_enrollments e
              LEFT JOIN tb_completions cp ON cp.enrollment_id = e.id
             WHERE e.status IN ('APPLIED', 'WAITLISTED', 'APPROVED') OR cp.status = 'COMPLETED'""");
        generate("2025-11-01", "2026-10-29");
        print("바뀐 강좌 이름 일부", """
            SELECT course_code, title FROM tb_courses WHERE course_code LIKE 'SYN-%' ORDER BY random() LIMIT 12""");
        print("바뀐 기관 이름", "SELECT institution_code, name FROM tb_institutions WHERE institution_code LIKE 'SYN-%'");
        print("회원 연령대·성별", """
            SELECT LEAST(GREATEST((EXTRACT(YEAR FROM age(birth_date))::int / 10) * 10, 10), 70) band, COUNT(*) n,
                   COUNT(*) FILTER (WHERE gender = 'FEMALE') f, COUNT(*) FILTER (WHERE gender = 'MALE') m
              FROM tb_users WHERE email LIKE '%@synthetic.invalid' GROUP BY 1 ORDER BY 1""");
        print("바뀐 회원 이름 일부(출생연도·성별)", """
            SELECT EXTRACT(YEAR FROM birth_date)::int, gender, name FROM tb_users
             WHERE email LIKE '%@synthetic.invalid' ORDER BY random() LIMIT 40""");
        print("회원 이름 글자 수", """
            SELECT char_length(name), COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid' GROUP BY 1 ORDER BY 1""");
        print("2·4·5글자 이름 예", """
            SELECT EXTRACT(YEAR FROM birth_date)::int, gender, name FROM tb_users
             WHERE email LIKE '%@synthetic.invalid' AND char_length(name) <> 3 ORDER BY random() LIMIT 15""");

        print("월별 (방문/검색/배너/조회)", """
            SELECT m, SUM(a) AS visits, SUM(b) AS searches, SUM(c) AS clicks, SUM(v) AS views FROM (
              SELECT to_char(event_date, 'YYYY-MM') m, COUNT(*) FILTER (WHERE event_type = 'VISIT') a,
                     COUNT(*) FILTER (WHERE event_type = 'SEARCH') b, COUNT(*) FILTER (WHERE event_type = 'BANNER_CLICK') c, 0 v
                FROM tb_usage_events WHERE viewer_key LIKE 'demo%' GROUP BY 1
              UNION ALL SELECT to_char(view_date, 'YYYY-MM'), 0, 0, 0, COUNT(*)
                FROM tb_course_views WHERE viewer_key LIKE 'demo%' GROUP BY 1) x GROUP BY m ORDER BY m""");
        print("최근 14일 방문자", """
            SELECT event_date, to_char(event_date, 'Dy') dow, COUNT(*) visitors FROM tb_usage_events
             WHERE viewer_key LIKE 'demo%' AND event_type = 'VISIT' GROUP BY 1 ORDER BY 1 DESC LIMIT 14""");
        print("검색어 상위 15", """
            SELECT target_key, COUNT(*) n, bool_or(zero_result) zero FROM tb_usage_events
             WHERE viewer_key LIKE 'demo%' AND event_type = 'SEARCH' GROUP BY 1 ORDER BY 2 DESC LIMIT 15""");
        print("결과 0건 검색어", """
            SELECT target_key, COUNT(*) n FROM tb_usage_events
             WHERE viewer_key LIKE 'demo%' AND event_type = 'SEARCH' AND zero_result GROUP BY 1 ORDER BY 2 DESC""");
        print("방문 회원·성별", """
            SELECT member_type, gender, COUNT(*) n FROM tb_usage_events
             WHERE viewer_key LIKE 'demo%' AND event_type = 'VISIT' GROUP BY 1, 2 ORDER BY 1, 3 DESC""");
        print("방문 회원 연령대", """
            SELECT age_band, COUNT(*) n FROM tb_usage_events
             WHERE viewer_key LIKE 'demo%' AND event_type = 'VISIT' AND member_type = 'MEMBER' GROUP BY 1 ORDER BY 1""");
        print("배너별 클릭", """
            SELECT target_key, COUNT(*) n FROM tb_usage_events
             WHERE viewer_key LIKE 'demo%' AND event_type = 'BANNER_CLICK' GROUP BY 1 ORDER BY 2 DESC""");
        print("최근 30일 신청 대비", """
            SELECT (SELECT COUNT(*) FROM tb_course_views WHERE view_date >= DATE '2026-09-30') views,
                   (SELECT COUNT(*) FROM tb_enrollments WHERE applied_at >= TIMESTAMPTZ '2026-09-30 00:00+09') enrollments""");
        print("행 수", """
            SELECT (SELECT COUNT(*) FROM tb_usage_events WHERE viewer_key LIKE 'demo%') events,
                   (SELECT COUNT(*) FROM tb_course_views WHERE viewer_key LIKE 'demo%') views""");
    }

    @Test
    void addInterestsGivesAboutSixtyPercentUpToThreeWithConsentAndRemoveClearsThem() throws IOException {
        jdbcTemplate.update("DELETE FROM tb_user_interests i USING tb_users u WHERE u.id = i.user_id AND u.email LIKE '%@synthetic.invalid'");
        run(Path.of("scripts/usage-demo/add-interests.sql"));

        long members = count("SELECT COUNT(*) FROM tb_users WHERE email LIKE '%@synthetic.invalid'");
        long selected = count("""
            SELECT COUNT(DISTINCT i.user_id) FROM tb_user_interests i JOIN tb_users u ON u.id = i.user_id
             WHERE u.email LIKE '%@synthetic.invalid'
            """);
        assertThat(selected).isBetween(members * 45 / 100, members * 75 / 100);
        assertThat(count("""
            SELECT COUNT(*) FROM (SELECT user_id FROM tb_user_interests GROUP BY user_id HAVING COUNT(*) > 3) t
            """)).isZero();
        assertThat(count("""
            SELECT COUNT(*) FROM tb_user_interests i JOIN tb_course_classifications c ON c.code = i.classification_code
             WHERE c.level <> 1
            """)).isZero();
        assertThat(count("""
            SELECT COUNT(DISTINCT i.user_id) FROM tb_user_interests i JOIN tb_users u ON u.id = i.user_id
             JOIN tb_user_agreements a ON a.user_id = i.user_id AND a.agreement_type = 'INTEREST' AND a.agreed
             WHERE u.email LIKE '%@synthetic.invalid'
            """)).isEqualTo(selected);

        long rowsBefore = count("SELECT COUNT(*) FROM tb_user_interests");
        run(Path.of("scripts/usage-demo/add-interests.sql"));
        assertThat(count("SELECT COUNT(*) FROM tb_user_interests")).isEqualTo(rowsBefore);
    }

    private void generate(String from, String to) throws IOException {
        jdbcTemplate.execute("DROP TABLE IF EXISTS demo_params");
        jdbcTemplate.execute("CREATE TEMP TABLE demo_params (f date, t date)");
        jdbcTemplate.update("INSERT INTO demo_params VALUES (?::date, ?::date)", from, to);
        jdbcTemplate.execute(Files.readString(GENERATE, StandardCharsets.UTF_8));
    }

    private double weekdayAverage(String from, String to) {
        return jdbcTemplate.queryForObject("""
            SELECT AVG(n) FROM (SELECT COUNT(*) n FROM tb_usage_events
             WHERE viewer_key LIKE 'demo%' AND event_type = 'VISIT' AND event_date BETWEEN ?::date AND ?::date
             GROUP BY event_date) x
            """, Double.class, from, to);
    }

    private void run(Path sql) throws IOException {
        jdbcTemplate.execute(Files.readString(sql, StandardCharsets.UTF_8));
    }

    private List<Map<String, Object>> visitorsPerDay(String from, String to) {
        return jdbcTemplate.queryForList("""
            SELECT event_date, COUNT(*) AS visitors FROM tb_usage_events
             WHERE viewer_key LIKE 'demo%' AND event_type = 'VISIT' AND event_date BETWEEN ?::date AND ?::date
             GROUP BY event_date ORDER BY event_date
            """, from, to);
    }

    private static double average(List<Map<String, Object>> rows) {
        return rows.stream().mapToInt(row -> ((Number) row.get("visitors")).intValue()).average().orElse(0);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private void print(String title, String sql) {
        System.out.println("== " + title);
        jdbcTemplate.queryForList(sql).forEach(row -> System.out.println("   " + row.values()));
    }
}
