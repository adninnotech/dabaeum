package com.adn.dabaeum.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.search.application.SearchService;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 설계서 §5 — 네 탭 부분일치, 노출 규칙, LIKE 특수문자 이스케이프. */
class SearchIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired SearchService service;

    @Test
    void findsAcrossTabsAndHidesNonPublicRows() {
        String word = "검색시험" + UUID.randomUUID().toString().substring(0, 6);
        UUID institution = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name, region) VALUES (?, ?, ?, '대구')",
            institution, "srch" + institution.toString().substring(0, 8), word + " 평생학습관");
        insertCourse(institution, word + " 도자기 교실", "RECRUITING");
        insertCourse(institution, word + " 초안 강좌", "DRAFT");
        jdbcTemplate.update("""
            INSERT INTO tb_notices (id, title, body, audience, status, published_at, created_at, updated_at)
            VALUES (?, ?, '본문', 'PUBLIC', 'PUBLISHED', now() - interval '1 hour', now(), now())
            """, UUID.randomUUID(), word + " 모집 공지");
        jdbcTemplate.update("""
            INSERT INTO tb_notices (id, title, body, audience, status, created_at, updated_at)
            VALUES (?, ?, '본문', 'PUBLIC', 'DRAFT', now(), now())
            """, UUID.randomUUID(), word + " 초안 공지");
        jdbcTemplate.update("INSERT INTO tb_faqs (id, question, answer, sort_order, created_at, updated_at) VALUES (?, ?, '답', 1, now(), now())",
            UUID.randomUUID(), word + " 신청은 어떻게 하나요");

        SearchService.Result result = service.search(word, 5);

        assertThat(result.courses().total()).isEqualTo(1);
        assertThat(result.courses().items()).singleElement()
            .satisfies(hit -> assertThat(hit.institutionName()).isEqualTo(word + " 평생학습관"));
        assertThat(result.institutions().total()).isEqualTo(1);
        assertThat(result.notices().total()).isEqualTo(1);
        assertThat(result.faqs().total()).isEqualTo(1);
    }

    @Test
    void percentSignIsMatchedLiterally() {
        UUID institution = UUID.randomUUID();
        String marker = UUID.randomUUID().toString().substring(0, 6);
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institution, "pct" + institution.toString().substring(0, 8), "퍼센트 기관 " + marker);
        insertCourse(institution, "할인 100% " + marker, "RECRUITING");
        insertCourse(institution, "할인 1000 " + marker, "RECRUITING");

        assertThat(service.search("100% " + marker, 5).courses().total()).isEqualTo(1);
    }

    private void insertCourse(UUID institution, String title, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (id, institution_id, course_code, title, education_type, start_date, end_date,
                                    capacity, status)
            VALUES (?, ?, ?, ?, 'OFFLINE', DATE '2026-10-01', DATE '2026-10-31', 10, ?)
            """, id, institution, "SRCH-" + id.toString().substring(0, 8), title, status);
    }
}
