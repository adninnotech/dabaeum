package com.adn.dabaeum.classification;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.classification.application.CourseClassificationNode;
import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 설계서 §12 — V41 표준 분류 시드와 트리, 제약. 제약 위반은 한 테스트에 하나씩. */
class CourseClassificationIntegrationTest extends RemotePostgresIntegrationTestSupport {

    @Autowired CourseClassificationService service;

    @Test
    void seedHasSevenFirstEighteenSecondAndFiftyTwoThirdLevelCodes() {
        assertThat(count(1)).isEqualTo(7);
        assertThat(count(2)).isEqualTo(18);
        assertThat(count(3)).isEqualTo(52);
    }

    @Test
    void treeNestsThreeLevelsInSortOrder() {
        List<CourseClassificationNode> tree = service.tree(false);

        assertThat(tree).extracting(CourseClassificationNode::code)
            .containsExactly("TSIX_010000", "TSIX_020000", "TSIX_030000", "TSIX_040000",
                "TSIX_050000", "TSIX_060000", "TSIX_070000");
        CourseClassificationNode culture = tree.get(3);
        assertThat(culture.name()).isEqualTo("문화예술교육");
        assertThat(culture.children().get(1).children()).extracting(CourseClassificationNode::name)
            .containsExactly("생활공예", "사진강좌", "노래교실");
        assertThat(tree.get(6).children()).isEmpty();
        assertThat(service.pathOf("TSIX_040201")).containsExactly("문화예술교육", "생활문화예술", "생활공예");
    }

    @Test
    void disabledNodeAndItsSubtreeLeaveThePublicTree() {
        service.setEnabled("TSIX_040200", false);

        List<CourseClassificationNode> publicTree = service.tree(true);
        CourseClassificationNode culture = publicTree.stream()
            .filter(node -> node.code().equals("TSIX_040000")).findFirst().orElseThrow();
        assertThat(culture.children()).extracting(CourseClassificationNode::code)
            .containsExactly("TSIX_040100", "TSIX_040300");
        assertThat(service.tree(false).get(3).children().get(1).enabled()).isFalse();
    }

    @Test
    void codeFormatIsEnforced() {
        assertSqlState("23514", () -> jdbcTemplate.update(
            "INSERT INTO tb_course_classifications (code, level, name, sort_order) VALUES ('BAD_1', 1, 'x', 99)"));
    }

    @Test
    void courseClassificationMustExist() {
        UUID courseId = UUID.randomUUID();
        UUID institutionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            institutionId, "cls" + institutionId.toString().substring(0, 8), "분류 기관");

        assertSqlState("23503", () -> jdbcTemplate.update("""
            INSERT INTO tb_courses (id, institution_id, course_code, title, education_type,
                                    start_date, end_date, capacity, classification_code)
            VALUES (?, ?, 'CLS-1', '분류 없는 강좌', 'OFFLINE', DATE '2026-10-01', DATE '2026-10-31', 10, 'TSIX_999999')
            """, courseId, institutionId));
    }

    private int count(int level) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_course_classifications WHERE level = ?", Integer.class, level);
    }
}
