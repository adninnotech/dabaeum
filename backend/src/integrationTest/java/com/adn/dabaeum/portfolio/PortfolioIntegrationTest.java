package com.adn.dabaeum.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.portfolio.application.PortfolioService;
import com.adn.dabaeum.portfolio.domain.PortfolioRepository;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 설계서 §6 — 포트폴리오 모으기, 공유 링크 생성·조회 수·폐기·보존 삭제. */
class PortfolioIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired PortfolioService service;
    @Autowired PortfolioRepository repository;

    @Test
    void portfolioAndShareLifecycle() {
        UUID user = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name) VALUES (?, ?)", user, "포트폴리오 학습자");
        UUID institution = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institution, "pf" + institution.toString().substring(0, 8), "포트폴리오 기관");
        UUID course = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (id, institution_id, course_code, title, education_type, start_date, end_date,
                                    capacity, status, classification_code)
            VALUES (?, ?, ?, '도자기 교실', 'OFFLINE', DATE '2026-10-01', DATE '2026-10-31', 10, 'COMPLETED', 'TSIX_040201')
            """, course, institution, "PF-" + course.toString().substring(0, 8));
        jdbcTemplate.update("""
            INSERT INTO tb_course_sessions (id, course_id, session_no, starts_at, ends_at)
            VALUES (?, ?, 1, TIMESTAMPTZ '2026-10-01 10:00+09', TIMESTAMPTZ '2026-10-01 12:00+09')
            """, UUID.randomUUID(), course);
        UUID enrollment = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_enrollments (id, course_id, user_id, status) VALUES (?, ?, ?, 'APPROVED')",
            enrollment, course, user);
        jdbcTemplate.update("""
            INSERT INTO tb_completions (id, enrollment_id, status, completed_at, confirmed_at)
            VALUES (?, ?, 'COMPLETED', now(), now())
            """, UUID.randomUUID(), enrollment);

        PortfolioService.Portfolio portfolio = service.get(user);
        assertThat(portfolio.summary().completedCourseCount()).isEqualTo(1);
        assertThat(portfolio.summary().totalLearningHours()).isEqualByComparingTo("2.0");
        assertThat(portfolio.categoryDistribution()).singleElement()
            .satisfies(item -> assertThat(item.classificationName()).isEqualTo("문화예술교육"));

        PortfolioService.CreatedShare created = service.createShare(user, List.of(), List.of(), 7);
        assertThat(service.open(created.token()).name()).isEqualTo("포트폴리오 학습자");
        service.open(created.token());
        assertThat(repository.shares(user)).singleElement().satisfies(share -> assertThat(share.viewCount()).isEqualTo(2));

        service.revokeShare(user, created.shareId());
        assertThatThrownBy(() -> service.open(created.token())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.revokeShare(UUID.randomUUID(), created.shareId())).isInstanceOf(ApiException.class);

        // 보존: 폐기 시각이 기준보다 앞이면 지워진다.
        assertThat(repository.deleteSharesEndedBefore(Instant.now().plus(1, ChronoUnit.DAYS))).isPositive();
        assertThat(repository.shares(user)).isEmpty();
    }
}
