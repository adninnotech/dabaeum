package com.adn.dabaeum.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.dashboard.domain.DashboardRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Task 9 의 함정 — 강사를 tb_user_roles 행 수로 세면 여러 기관에 소속된 강사가 여러 번 잡힌다.
 * 공유 DB 라 절대값이 아니라, 이 테스트가 만든 사용자로 인한 증가분(delta)만 검증한다.
 */
class PlatformDashboardInstructorDistinctIntegrationTest
    extends RemotePostgresIntegrationTestSupport {

    @Autowired
    private DashboardRepository dashboardRepository;

    @Test
    void 한_강사가_두_기관에_소속돼도_전체_강사수는_한번만_는다() {
        long before = dashboardRepository
            .platformDashboard(5, Instant.now())
            .instructorSummary().total();

        UUID institutionA = insertInstitution();
        UUID institutionB = insertInstitution();
        UUID userId = insertUser();
        insertInstructorRole(userId, institutionA);
        insertInstructorRole(userId, institutionB);

        long after = dashboardRepository
            .platformDashboard(5, Instant.now())
            .instructorSummary().total();

        assertThat(after - before).isEqualTo(1);
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

    // 타임스탬프는 OffsetDateTime 으로 바인딩한다 — Instant 는 드라이버가 받지 않는다.
    private void insertInstructorRole(UUID userId, UUID institutionId) {
        jdbcTemplate.update("""
            INSERT INTO tb_user_roles (id, user_id, institution_id, role, created_at)
            VALUES (?, ?, ?, 'INSTRUCTOR', ?)
            """,
            UUID.randomUUID(), userId, institutionId, OffsetDateTime.now());
    }
}
