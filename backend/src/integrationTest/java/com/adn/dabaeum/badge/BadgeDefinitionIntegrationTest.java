package com.adn.dabaeum.badge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.adn.dabaeum.badge.application.BadgeAwarder;
import com.adn.dabaeum.badge.application.BadgeDefinitionCommand;
import com.adn.dabaeum.badge.application.BadgeDefinitionService;
import com.adn.dabaeum.badge.domain.BadgeDefinition;
import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.badge.domain.BadgeRuleType;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 설계서 §11 — 정의 저장(JSONB)과 수료 확정 시 자동 발급(사람당 정의당 1회), 확정 되돌리기 시 재평가. */
class BadgeDefinitionIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Autowired BadgeDefinitionService definitions;
    @Autowired BadgeAwarder awarder;
    @Autowired com.adn.dabaeum.badge.domain.LearningBadgeRepository badges;
    @Autowired com.adn.dabaeum.badge.domain.LearningBadgeEventRepository events;
    @Autowired com.adn.dabaeum.badge.application.BadgeStatusService status;
    @Autowired com.adn.dabaeum.portfolio.application.PortfolioService portfolios;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;

    @Test
    void ruleParamsRoundTripThroughJsonb() {
        BadgeDefinition created = definitions.create(command(unique("CULTURE"), BadgeRuleType.CUMULATIVE, null, "TSIX_040000", 2));

        assertThat(definitions.get(created.id()).rule()).isEqualTo(new BadgeRule.Cumulative(2, "TSIX_040000"));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT rule_params ->> 'classificationCode' FROM tb_badge_definitions WHERE id = ?", String.class, created.id()))
            .isEqualTo("TSIX_040000");
    }

    @Test
    void duplicateCodeConflicts() {
        String code = unique("DUP");
        definitions.create(command(code, BadgeRuleType.CUMULATIVE, null, null, 1));

        assertThatThrownBy(() -> definitions.create(command(code, BadgeRuleType.CUMULATIVE, null, null, 1)))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.BADGE_DEFINITION_CONFLICT));
    }

    @Test
    void completionAwardsClassificationAndCumulativeBadgesOnlyOnce() {
        UUID institution = insertInstitution();
        UUID user = insertUser();
        UUID first = insertCourse(institution, "TSIX_040201");
        UUID second = insertCourse(institution, "TSIX_040301");
        BadgeDefinition anyCulture = definitions.create(
            command(unique("CULTURE_ANY"), BadgeRuleType.COURSE_COMPLETION, null, "TSIX_040000", null));
        BadgeDefinition twoCulture = definitions.create(
            command(unique("CULTURE_TWO"), BadgeRuleType.CUMULATIVE, null, "TSIX_040000", 2));

        UUID completion1 = completed(user, first);
        awarder.onCompletionConfirmed(completion1, user, first, NOW);
        assertThat(badges(user, anyCulture.id())).isEqualTo(1);
        assertThat(badges(user, twoCulture.id())).isZero();

        UUID completion2 = completed(user, second);
        awarder.onCompletionConfirmed(completion2, user, second, NOW);
        awarder.onCompletionConfirmed(completion2, user, second, NOW);

        assertThat(badges(user, anyCulture.id())).isEqualTo(1);
        assertThat(badges(user, twoCulture.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT course_id FROM tb_learning_badges WHERE user_id = ? AND definition_id = ?", UUID.class,
            user, anyCulture.id())).isEqualTo(first);
        assertThat(badges.findByUserIdAndDefinitionId(user, anyCulture.id())).hasValueSatisfying(badge -> {
            assertThat(badge.definitionCode()).isEqualTo(anyCulture.code());
            assertThat(badge.badgeName()).isEqualTo(anyCulture.name());
            assertThat(badge.badgeType()).isEqualTo("COURSE_COMPLETION");
        });
        assertThatThrownBy(() -> definitions.delete(anyCulture.id()))
            .isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo(ApiErrorCode.BADGE_DEFINITION_IN_USE));
    }

    @Test
    void revertRevokesBrokenAutoBadgesAndReconfirmRestoresThem() {
        UUID institution = insertInstitution();
        UUID user = insertUser();
        UUID first = insertCourse(institution, "TSIX_040201");
        UUID second = insertCourse(institution, "TSIX_040301");
        BadgeDefinition anyCulture = definitions.create(
            command(unique("REV_ANY"), BadgeRuleType.COURSE_COMPLETION, null, "TSIX_040000", null));
        BadgeDefinition twoCulture = definitions.create(
            command(unique("REV_TWO"), BadgeRuleType.CUMULATIVE, null, "TSIX_040000", 2));
        UUID completion1 = completed(user, first);
        awarder.onCompletionConfirmed(completion1, user, first, NOW);
        UUID completion2 = completed(user, second);
        awarder.onCompletionConfirmed(completion2, user, second, NOW);

        // 두 번째 수료를 되돌리면 누적 2개는 깨지고, 분류 1개는 첫 수료로 여전히 채워진다.
        jdbcTemplate.update("UPDATE tb_completions SET status = 'ELIGIBLE', completed_at = NULL, confirmed_at = NULL WHERE id = ?",
            completion2);
        sqlSession.clearCache();
        Instant revertedAt = NOW.plusSeconds(60);
        awarder.onCompletionReverted(completion2, user, second, revertedAt);

        assertThat(status(user, twoCulture.id())).isEqualTo("REVOKED");
        assertThat(status(user, anyCulture.id())).isEqualTo("ISSUED");
        UUID twoBadge = badges.findByUserIdAndDefinitionId(user, twoCulture.id()).orElseThrow().id();
        assertThat(events.findByBadgeId(twoBadge).getFirst().type().name()).isEqualTo("REVOKED");

        // 다시 확정하면 같은 행이 ISSUED 로 돌아온다(사람당 정의당 1행 유지).
        jdbcTemplate.update("UPDATE tb_completions SET status = 'COMPLETED', completed_at = now(), confirmed_at = now() WHERE id = ?",
            completion2);
        sqlSession.clearCache();
        awarder.onCompletionConfirmed(completion2, user, second, revertedAt.plusSeconds(60));

        assertThat(status(user, twoCulture.id())).isEqualTo("ISSUED");
        assertThat(badges(user, twoCulture.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT revoked_at IS NULL FROM tb_learning_badges WHERE user_id = ? AND definition_id = ?", Boolean.class,
            user, twoCulture.id())).isTrue();
    }

    @Test
    void seededTierBadgeRisesWithCompletionsAndShowsInSearchAndPortfolio() {
        UUID institution = insertInstitution();
        UUID user = insertUser();
        jdbcTemplate.update("UPDATE tb_users SET name = ? WHERE id = ?", "단계학습자" + user.toString().substring(0, 6), user);
        UUID tier = jdbcTemplate.queryForObject(
            "SELECT id FROM tb_badge_definitions WHERE code = 'LIFELONG_LEARNING_TIER'", UUID.class);

        UUID first = insertCourse(institution, "TSIX_040201");
        awarder.onCompletionConfirmed(completed(user, first), user, first, NOW);
        assertThat(tierLevel(user, tier)).isEqualTo(1);
        UUID last = null;
        UUID lastCompletion = null;
        for (int i = 2; i <= 6; i++) {
            last = insertCourse(institution, "TSIX_040201");
            lastCompletion = completed(user, last);
        }
        awarder.onCompletionConfirmed(lastCompletion, user, last, NOW.plusSeconds(60));

        assertThat(tierLevel(user, tier)).isEqualTo(2);
        UUID badgeId = badges.findByUserIdAndDefinitionId(user, tier).orElseThrow().id();
        assertThat(events.findByBadgeId(badgeId)).extracting(event -> event.type().name())
            .containsExactly("RENEWED", "ISSUED");
        assertThat(badges.findById(badgeId).orElseThrow().details()).satisfies(details -> {
            assertThat(details.tierLevel()).isEqualTo(2);
            assertThat(details.recipientName()).startsWith("단계학습자");
            assertThat(details.lastEvent().type().name()).isEqualTo("RENEWED");
            assertThat(details.rule()).isInstanceOf(BadgeRule.Tiered.class);
        });

        var page = status.search(tier, null, 2, "단계학습자" + user.toString().substring(0, 6), 0, 20);
        assertThat(page.data()).extracting(com.adn.dabaeum.badge.domain.LearningBadge::id).containsExactly(badgeId);

        var portfolio = portfolios.get(user);
        assertThat(portfolio.badges()).anySatisfy(item -> {
            assertThat(item.tierLevel()).isEqualTo(2);
            assertThat(item.tierName()).isEqualTo("새싹");
        });
        assertThat(portfolio.badgeCatalog()).anySatisfy(item -> {
            assertThat(item.definition().id()).isEqualTo(tier);
            assertThat(item.owned()).isTrue();
        });
    }

    @Test
    void adminRevokedBadgeStaysRevokedWhenRuleIsMetAgain() {
        UUID institution = insertInstitution();
        UUID user = insertUser();
        UUID admin = insertUser();
        BadgeDefinition anyCulture = definitions.create(
            command(unique("ADM_REV"), BadgeRuleType.COURSE_COMPLETION, null, "TSIX_040000", null));
        UUID first = insertCourse(institution, "TSIX_040201");
        awarder.onCompletionConfirmed(completed(user, first), user, first, NOW);
        UUID badgeId = badges.findByUserIdAndDefinitionId(user, anyCulture.id()).orElseThrow().id();

        var revoked = status.revoke(badgeId, "테스트 데이터 정리", admin);

        assertThat(revoked.status().name()).isEqualTo("REVOKED");
        assertThat(events.findByBadgeId(badgeId).getFirst()).satisfies(event -> {
            assertThat(event.type().name()).isEqualTo("REVOKED");
            assertThat(event.actorId()).isEqualTo(admin);
        });
        UUID second = insertCourse(institution, "TSIX_040301");
        awarder.onCompletionConfirmed(completed(user, second), user, second, NOW.plusSeconds(60));
        assertThat(status(user, anyCulture.id())).isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_users WHERE id = ?", Integer.class, user))
            .isEqualTo(1);
    }

    private Integer tierLevel(UUID user, UUID definition) {
        return jdbcTemplate.queryForObject(
            "SELECT tier_level FROM tb_learning_badges WHERE user_id = ? AND definition_id = ?", Integer.class,
            user, definition);
    }

    private String status(UUID user, UUID definition) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM tb_learning_badges WHERE user_id = ? AND definition_id = ?", String.class, user, definition);
    }

    private int badges(UUID user, UUID definition) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_learning_badges WHERE user_id = ? AND definition_id = ?", Integer.class, user, definition);
    }

    private UUID completed(UUID user, UUID course) {
        UUID enrollment = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_enrollments (id, course_id, user_id, status) VALUES (?, ?, ?, 'APPROVED')",
            enrollment, course, user);
        UUID completion = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_completions (id, enrollment_id, status, completed_at, confirmed_at)
            VALUES (?, ?, 'COMPLETED', now(), now())
            """, completion, enrollment);
        // 테스트는 한 트랜잭션이라 jdbcTemplate 로 넣은 수료가 MyBatis 1차 캐시에 가린다. 운영은 확정마다 트랜잭션이 따로다.
        sqlSession.clearCache();
        return completion;
    }

    private UUID insertInstitution() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_institutions (id, institution_code, name) VALUES (?, ?, ?)",
            id, "bdg" + id.toString().substring(0, 8), "배지 기관");
        return id;
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tb_users (id, name) VALUES (?, ?)", id, "배지 학습자");
        return id;
    }

    private UUID insertCourse(UUID institution, String classification) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tb_courses (id, institution_id, course_code, title, education_type,
                                    start_date, end_date, capacity, classification_code)
            VALUES (?, ?, ?, '배지 강좌', 'OFFLINE', DATE '2026-10-01', DATE '2026-10-31', 10, ?)
            """, id, institution, "BDG-" + id.toString().substring(0, 8), classification);
        return id;
    }

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase().replace('-', '_');
    }

    private static BadgeDefinitionCommand command(
        String code, BadgeRuleType type, UUID courseId, String classification, Integer count
    ) {
        return new BadgeDefinitionCommand(code, "배지 " + code, null, null, type, courseId, classification, count);
    }
}
