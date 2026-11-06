package com.adn.dabaeum.badge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.badge.domain.BadgeDefinition;
import com.adn.dabaeum.badge.domain.BadgeDefinitionRepository;
import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.badge.domain.BadgeRuleType;
import com.adn.dabaeum.classification.application.CourseClassificationService;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.course.domain.CourseRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 설계서 §11 — 정의 관리 규칙. */
@ExtendWith(MockitoExtension.class)
class BadgeDefinitionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final UUID ID = UUID.randomUUID();

    @Mock BadgeDefinitionRepository repository;
    @Mock CourseRepository courses;
    @Mock CourseClassificationService classifications;
    @Mock com.adn.dabaeum.file.domain.StoredFileRepository files;
    private BadgeDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new BadgeDefinitionService(repository, courses, classifications, files, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsCumulativeDefinitionUnderAClassification() {
        when(classifications.exists("TSIX_040000")).thenReturn(true);

        BadgeDefinition created = service.create(cumulative("CULTURE_3", 3, "TSIX_040000"));

        assertThat(created.rule()).isEqualTo(new BadgeRule.Cumulative(3, "TSIX_040000"));
        assertThat(created.active()).isTrue();
        verify(repository).save(created);
    }

    @Test
    void unknownClassificationIsRejected() {
        when(classifications.exists("TSIX_990000")).thenReturn(false);

        assertCode(() -> service.create(cumulative("X_1", 1, "TSIX_990000")), ApiErrorCode.VALIDATION_FAILED);
        verify(repository, never()).save(any());
    }

    @Test
    void courseCompletionWithBothTargetsIsRejected() {
        BadgeDefinitionCommand both = new BadgeDefinitionCommand("C_1", "강좌", null, null,
            BadgeRuleType.COURSE_COMPLETION, UUID.randomUUID(), "TSIX_040201", null);

        assertCode(() -> service.create(both), ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void createsTieredDefinitionWithLevels() {
        java.util.List<BadgeRule.TierLevel> levels = java.util.List.of(
            new BadgeRule.TierLevel(1, "씨앗", 1), new BadgeRule.TierLevel(2, "새싹", 6));

        BadgeDefinition created = service.create(new BadgeDefinitionCommand("TIER_X", "단계", null, null,
            BadgeRuleType.TIERED, null, null, null, levels));

        assertThat(created.rule()).isEqualTo(new BadgeRule.Tiered(levels, null));
    }

    @Test
    void tieredWithoutValidLevelsIsRejected() {
        assertCode(() -> service.create(new BadgeDefinitionCommand("TIER_X", "단계", null, null,
            BadgeRuleType.TIERED, null, null, null, java.util.List.of())), ApiErrorCode.VALIDATION_FAILED);
        verify(repository, never()).save(any());
    }

    @Test
    void tierNamesAndImagesCanChangeAfterIssuanceButThresholdsCannot() {
        UUID image = UUID.randomUUID();
        when(files.findById(image)).thenReturn(Optional.of(org.mockito.Mockito.mock(com.adn.dabaeum.file.domain.StoredFile.class)));
        when(repository.findById(ID)).thenReturn(Optional.of(existing(new BadgeRule.Tiered(java.util.List.of(
            new BadgeRule.TierLevel(1, "씨앗", 1), new BadgeRule.TierLevel(2, "새싹", 6)), null))));
        when(repository.hasIssuedBadges(ID)).thenReturn(true);

        BadgeDefinition renamed = service.update(ID, tiered(java.util.List.of(
            new BadgeRule.TierLevel(1, "첫걸음", 1, image), new BadgeRule.TierLevel(2, "새싹", 6))));
        assertThat(((BadgeRule.Tiered) renamed.rule()).levels().getFirst().name()).isEqualTo("첫걸음");
        assertThat(((BadgeRule.Tiered) renamed.rule()).levels().getFirst().imageFileId()).isEqualTo(image);

        assertCode(() -> service.update(ID, tiered(java.util.List.of(
            new BadgeRule.TierLevel(1, "씨앗", 1), new BadgeRule.TierLevel(2, "새싹", 5)))),
            ApiErrorCode.BADGE_DEFINITION_IN_USE);
    }

    @Test
    void unknownTierImageIsRejected() {
        UUID image = UUID.randomUUID();
        when(files.findById(image)).thenReturn(Optional.empty());

        assertCode(() -> service.create(tiered(java.util.List.of(
            new BadgeRule.TierLevel(1, "씨앗", 1, image), new BadgeRule.TierLevel(2, "새싹", 6)))),
            ApiErrorCode.VALIDATION_FAILED);
    }

    private static BadgeDefinitionCommand tiered(java.util.List<BadgeRule.TierLevel> levels) {
        return new BadgeDefinitionCommand("TIER_X", "단계", null, null, BadgeRuleType.TIERED, null, null, null, levels);
    }

    @Test
    void ruleCannotChangeAfterBadgesWereIssued() {
        when(repository.findById(ID)).thenReturn(Optional.of(existing(new BadgeRule.Cumulative(3, null))));
        when(repository.hasIssuedBadges(ID)).thenReturn(true);

        assertCode(() -> service.update(ID, cumulative("IGNORED", 5, null)), ApiErrorCode.BADGE_DEFINITION_IN_USE);
    }

    @Test
    void nameCanChangeAfterBadgesWereIssued() {
        when(repository.findById(ID)).thenReturn(Optional.of(existing(new BadgeRule.Cumulative(3, null))));

        BadgeDefinition updated = service.update(ID, new BadgeDefinitionCommand(
            null, "새 이름", null, null, BadgeRuleType.CUMULATIVE, null, null, 3));

        assertThat(updated.name()).isEqualTo("새 이름");
        assertThat(updated.code()).isEqualTo("CULTURE_3");
    }

    @Test
    void definitionWithIssuedBadgesCannotBeDeleted() {
        when(repository.findById(ID)).thenReturn(Optional.of(existing(new BadgeRule.Cumulative(3, null))));
        when(repository.hasIssuedBadges(ID)).thenReturn(true);

        assertCode(() -> service.delete(ID), ApiErrorCode.BADGE_DEFINITION_IN_USE);
        verify(repository, never()).delete(any());
    }

    private static BadgeDefinitionCommand cumulative(String code, int count, String classification) {
        return new BadgeDefinitionCommand(code, "문화예술 3과목", null, null, BadgeRuleType.CUMULATIVE, null, classification, count);
    }

    private static BadgeDefinition existing(BadgeRule rule) {
        return new BadgeDefinition(ID, "CULTURE_3", "문화예술 3과목", null, null, rule, true, NOW, NOW);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ApiErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class,
            exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
