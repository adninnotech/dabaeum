package com.adn.dabaeum.badge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adn.dabaeum.badge.domain.BadgeAwardRepository;
import com.adn.dabaeum.badge.domain.BadgeAwardRepository.AwardedBadge;
import com.adn.dabaeum.badge.domain.BadgeDefinition;
import com.adn.dabaeum.badge.domain.BadgeDefinitionRepository;
import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.badge.domain.LearningBadgeEvent;
import com.adn.dabaeum.badge.domain.LearningBadgeEventRepository;
import com.adn.dabaeum.badge.domain.LearningBadgeStatus;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 설계서 §11.2·§11.4 — 수료 확정 시 활성 정의를 평가해 발급·단계 갱신·복구하고,
 * 확정을 되돌리면 자동 배지를 다시 평가해 폐기·단계 하강한다. 모든 변동은 이력에 남는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BadgeAwarderTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID COURSE = UUID.randomUUID();
    private static final UUID COMPLETION = UUID.randomUUID();

    @Mock BadgeDefinitionRepository definitions;
    @Mock BadgeAwardRepository awards;
    @Mock CourseRepository courses;
    @Mock LearningBadgeEventRepository events;
    private BadgeAwarder awarder;

    @BeforeEach
    void setUp() {
        Course course = Mockito.mock(Course.class);
        when(course.classificationCode()).thenReturn("TSIX_040201");
        when(courses.findActiveById(COURSE)).thenReturn(Optional.of(course));
        when(awards.lockAwarded(any(), any())).thenReturn(Optional.empty());
        when(awards.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(awards.restore(any(), any(), any(), any())).thenReturn(true);
        when(awards.changeLevel(any(), anyInt(), any())).thenReturn(true);
        when(awards.revoke(any(), any())).thenReturn(true);
        awarder = new BadgeAwarder(definitions, awards, courses, events);
    }

    @Test
    void specificCourseCompletionAwardsWithCourseIdAndRecordsIssue() {
        BadgeDefinition definition = active(new BadgeRule.CourseCompletion(COURSE, null));

        awarder.onCompletionConfirmed(COMPLETION, USER, COURSE, NOW);

        verify(awards).insertIfAbsent(any(), eq(USER), eq(COURSE), eq(definition), isNull(), eq(NOW));
        assertThat(recorded()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(LearningBadgeEvent.Type.ISSUED);
            assertThat(event.actorId()).isNull();
        });
    }

    @Test
    void classificationCompletionIncludesChildren() {
        BadgeDefinition definition = active(new BadgeRule.CourseCompletion(null, "TSIX_040000"));

        awarder.onCompletionConfirmed(COMPLETION, USER, COURSE, NOW);

        verify(awards).insertIfAbsent(any(), eq(USER), eq(COURSE), eq(definition), isNull(), eq(NOW));
    }

    @Test
    void otherClassificationDoesNotAward() {
        active(new BadgeRule.CourseCompletion(null, "TSIX_050000"));

        awarder.onCompletionConfirmed(COMPLETION, USER, COURSE, NOW);

        verify(awards, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
        verify(events, never()).save(any());
    }

    @Test
    void cumulativeAwardsOnceCountIsReachedWithoutCourse() {
        BadgeDefinition definition = active(new BadgeRule.Cumulative(3, "TSIX_040000"));
        when(awards.countCompletedCourses(USER, "TSIX_04")).thenReturn(3L);

        awarder.onCompletionConfirmed(COMPLETION, USER, COURSE, NOW);

        verify(awards).insertIfAbsent(any(), eq(USER), isNull(), eq(definition), isNull(), eq(NOW));
    }

    @Test
    void cumulativeBelowCountDoesNotAward() {
        active(new BadgeRule.Cumulative(3, null));
        when(awards.countCompletedCourses(USER, null)).thenReturn(2L);

        awarder.onCompletionConfirmed(COMPLETION, USER, COURSE, NOW);

        verify(awards, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void alreadyIssuedBadgeIsLeftAlone() {
        BadgeDefinition definition = active(new BadgeRule.Cumulative(1, null));
        when(awards.countCompletedCourses(USER, null)).thenReturn(4L);
        when(awards.lockAwarded(USER, definition.id())).thenReturn(Optional.of(
            new AwardedBadge(UUID.randomUUID(), definition.id(), LearningBadgeStatus.ISSUED, null, null, false)));

        awarder.onCompletionConfirmed(COMPLETION, USER, COURSE, NOW);

        verify(awards, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
        verify(events, never()).save(any());
    }

    @Test
    void revokedAutoBadgeIsRestoredWhenRuleIsMetAgain() {
        BadgeDefinition definition = active(new BadgeRule.Cumulative(1, null));
        UUID badge = UUID.randomUUID();
        when(awards.countCompletedCourses(USER, null)).thenReturn(1L);
        when(awards.lockAwarded(USER, definition.id())).thenReturn(Optional.of(
            new AwardedBadge(badge, definition.id(), LearningBadgeStatus.REVOKED, null, null, false)));

        awarder.onCompletionConfirmed(COMPLETION, USER, COURSE, NOW);

        verify(awards).restore(badge, null, null, NOW);
        assertThat(recorded()).singleElement()
            .satisfies(event -> assertThat(event.type()).isEqualTo(LearningBadgeEvent.Type.RESTORED));
    }

    @Test
    void badgeRevokedByAdminIsNotRestoredAutomatically() {
        BadgeDefinition definition = active(new BadgeRule.Cumulative(1, null));
        when(awards.countCompletedCourses(USER, null)).thenReturn(3L);
        when(awards.lockAwarded(USER, definition.id())).thenReturn(Optional.of(
            new AwardedBadge(UUID.randomUUID(), definition.id(), LearningBadgeStatus.REVOKED, null, null, true)));

        awarder.onCompletionConfirmed(COMPLETION, USER, COURSE, NOW);

        verify(awards, never()).restore(any(), any(), any(), any());
        verify(events, never()).save(any());
    }

    @Test
    void tieredFirstCompletionIssuesTheReachedLevel() {
        BadgeDefinition definition = active(tiers());
        when(awards.countCompletedCourses(USER, null)).thenReturn(6L);

        awarder.onCompletionConfirmed(COMPLETION, USER, COURSE, NOW);

        verify(awards).insertIfAbsent(any(), eq(USER), isNull(), eq(definition), eq(2), eq(NOW));
        assertThat(recorded()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(LearningBadgeEvent.Type.ISSUED);
            assertThat(event.toLevel()).isEqualTo(2);
            assertThat(event.reason()).contains("6건").contains("새싹");
        });
    }

    @Test
    void tieredBadgeRisesAndRecordsRenewal() {
        BadgeDefinition definition = active(tiers());
        UUID badge = UUID.randomUUID();
        when(awards.countCompletedCourses(USER, null)).thenReturn(11L);
        when(awards.lockAwarded(USER, definition.id())).thenReturn(Optional.of(
            new AwardedBadge(badge, definition.id(), LearningBadgeStatus.ISSUED, 2, null, false)));

        awarder.onCompletionConfirmed(COMPLETION, USER, COURSE, NOW);

        verify(awards).changeLevel(badge, 3, NOW);
        assertThat(recorded()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(LearningBadgeEvent.Type.RENEWED);
            assertThat(event.fromLevel()).isEqualTo(2);
            assertThat(event.toLevel()).isEqualTo(3);
            assertThat(event.reason()).contains("새싹").contains("자람");
        });
    }

    @Test
    void tieredBadgeAtSameLevelIsUnchanged() {
        BadgeDefinition definition = active(tiers());
        when(awards.countCompletedCourses(USER, null)).thenReturn(7L);
        when(awards.lockAwarded(USER, definition.id())).thenReturn(Optional.of(
            new AwardedBadge(UUID.randomUUID(), definition.id(), LearningBadgeStatus.ISSUED, 2, null, false)));

        awarder.onCompletionConfirmed(COMPLETION, USER, COURSE, NOW);

        verify(awards, never()).changeLevel(any(), anyInt(), any());
        verify(events, never()).save(any());
    }

    @Test
    void revertRevokesCumulativeBadgeWhenCountDropsBelowRule() {
        BadgeDefinition definition = definition(new BadgeRule.Cumulative(3, "TSIX_040000"));
        UUID badge = autoBadge(definition, null);
        when(awards.countCompletedCourses(USER, "TSIX_04")).thenReturn(2L);

        awarder.onCompletionReverted(COMPLETION, USER, COURSE, NOW);

        verify(awards).revoke(badge, NOW);
        assertThat(recorded()).singleElement()
            .satisfies(event -> assertThat(event.type()).isEqualTo(LearningBadgeEvent.Type.REVOKED));
    }

    @Test
    void revertKeepsBadgeStillSatisfiedByOtherCompletions() {
        BadgeDefinition cumulative = definition(new BadgeRule.Cumulative(3, null));
        BadgeDefinition byClassification = definition(new BadgeRule.CourseCompletion(null, "TSIX_040000"));
        when(awards.findIssuedAutoBadges(USER)).thenReturn(List.of(
            new AwardedBadge(UUID.randomUUID(), cumulative.id()),
            new AwardedBadge(UUID.randomUUID(), byClassification.id())));
        when(awards.countCompletedCourses(USER, null)).thenReturn(3L);
        when(awards.countCompletedCourses(USER, "TSIX_04")).thenReturn(1L);

        awarder.onCompletionReverted(COMPLETION, USER, COURSE, NOW);

        verify(awards, never()).revoke(any(), any());
        verify(events, never()).save(any());
    }

    @Test
    void revertRevokesSpecificCourseBadgeWhenThatCourseIsNoLongerCompleted() {
        BadgeDefinition definition = definition(new BadgeRule.CourseCompletion(COURSE, null));
        UUID badge = autoBadge(definition, null);
        when(awards.countCompletedCourse(USER, COURSE)).thenReturn(0L);

        awarder.onCompletionReverted(COMPLETION, USER, COURSE, NOW);

        verify(awards).revoke(badge, NOW);
    }

    @Test
    void revertLowersTieredBadgeLevel() {
        BadgeDefinition definition = definition(tiers());
        UUID badge = autoBadge(definition, 2);
        when(awards.countCompletedCourses(USER, null)).thenReturn(5L);

        awarder.onCompletionReverted(COMPLETION, USER, COURSE, NOW);

        verify(awards).changeLevel(badge, 1, NOW);
        assertThat(recorded()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(LearningBadgeEvent.Type.RENEWED);
            assertThat(event.fromLevel()).isEqualTo(2);
            assertThat(event.toLevel()).isEqualTo(1);
        });
    }

    @Test
    void revertRevokesTieredBadgeBelowFirstLevel() {
        BadgeDefinition definition = definition(tiers());
        UUID badge = autoBadge(definition, 1);
        when(awards.countCompletedCourses(USER, null)).thenReturn(0L);

        awarder.onCompletionReverted(COMPLETION, USER, COURSE, NOW);

        verify(awards).revoke(badge, NOW);
        assertThat(recorded()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(LearningBadgeEvent.Type.REVOKED);
            assertThat(event.fromLevel()).isEqualTo(1);
        });
    }

    private List<LearningBadgeEvent> recorded() {
        ArgumentCaptor<LearningBadgeEvent> captor = ArgumentCaptor.forClass(LearningBadgeEvent.class);
        verify(events, Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private UUID autoBadge(BadgeDefinition definition, Integer level) {
        UUID badge = UUID.randomUUID();
        when(awards.findIssuedAutoBadges(USER)).thenReturn(List.of(
            new AwardedBadge(badge, definition.id(), LearningBadgeStatus.ISSUED, level, null, false)));
        return badge;
    }

    private BadgeDefinition active(BadgeRule rule) {
        BadgeDefinition definition = definition(rule);
        when(definitions.findAll(true)).thenReturn(List.of(definition));
        return definition;
    }

    private BadgeDefinition definition(BadgeRule rule) {
        BadgeDefinition definition = new BadgeDefinition(UUID.randomUUID(), "B_" + Math.abs(rule.hashCode() % 1000),
            "배지", null, null, rule, true, NOW, NOW);
        when(definitions.findById(definition.id())).thenReturn(Optional.of(definition));
        return definition;
    }

    private static BadgeRule.Tiered tiers() {
        return new BadgeRule.Tiered(List.of(
            new BadgeRule.TierLevel(1, "씨앗", 1), new BadgeRule.TierLevel(2, "새싹", 6),
            new BadgeRule.TierLevel(3, "자람", 11)), null);
    }
}
