package com.adn.dabaeum.badge.application;

import com.adn.dabaeum.badge.domain.BadgeAwardRepository;
import com.adn.dabaeum.badge.domain.BadgeAwardRepository.AwardedBadge;
import com.adn.dabaeum.badge.domain.BadgeDefinition;
import com.adn.dabaeum.badge.domain.BadgeDefinitionRepository;
import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.badge.domain.LearningBadgeEvent;
import com.adn.dabaeum.badge.domain.LearningBadgeEventRepository;
import com.adn.dabaeum.badge.domain.LearningBadgeStatus;
import com.adn.dabaeum.classification.domain.ClassificationPrefix;
import com.adn.dabaeum.completion.application.CompletionConfirmedListener;
import com.adn.dabaeum.course.domain.Course;
import com.adn.dabaeum.course.domain.CourseRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 수료가 확정되면 활성 정의를 모두 평가해 맞는 배지를 준다 (설계서 §11.2). 확정과 같은 트랜잭션이다.
 * 단계 배지(§11.4)는 이수 건수로 단계를 정해 없으면 발급, 높아지면 갱신한다. 폐기된 자동 배지는 조건을 다시 채우면 복구한다
 * (관리자가 사유를 달아 폐기한 배지는 되살리지 않는다).
 * 확정을 되돌리면 자동 배지를 다시 평가해 조건이 깨진 배지는 폐기하고 단계 배지는 낮춘다. 모든 변동은 이력에 남는다.
 * COURSE_COMPLETION 배지는 그 강좌를 가리키고(수료증은 확정 뒤에 따로 발급되므로 비운다), 나머지는 강좌도 비운다.
 */
@Component
@Profile({"local", "dev"})
public class BadgeAwarder implements CompletionConfirmedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeAwarder.class);

    private final BadgeDefinitionRepository definitions;
    private final BadgeAwardRepository awards;
    private final CourseRepository courses;
    private final LearningBadgeEventRepository events;

    public BadgeAwarder(BadgeDefinitionRepository definitions, BadgeAwardRepository awards, CourseRepository courses,
        LearningBadgeEventRepository events) {
        this.definitions = Objects.requireNonNull(definitions);
        this.awards = Objects.requireNonNull(awards);
        this.courses = Objects.requireNonNull(courses);
        this.events = Objects.requireNonNull(events);
    }

    @Override
    public void onCompletionConfirmed(UUID completionId, UUID userId, UUID courseId, Instant confirmedAt) {
        String courseClassification = courses.findActiveById(courseId).map(Course::classificationCode).orElse(null);
        for (BadgeDefinition definition : definitions.findAll(true)) {
            Target target = target(definition.rule(), userId, courseId, courseClassification);
            if (target != null) {
                UUID badgeCourseId = definition.rule() instanceof BadgeRule.CourseCompletion ? courseId : null;
                award(definition, userId, badgeCourseId, target, completionId, confirmedAt);
            }
        }
    }

    /**
     * 확정을 되돌리면 이 사람의 자동 배지를 남은 수료로 다시 평가한다. 조건이 깨진 배지는 폐기하고 단계 배지는 낮춘다.
     * 사용 중지된 정의도 평가한다. 수료증에 붙여 준 수동 배지는 건드리지 않는다.
     */
    @Override
    public void onCompletionReverted(UUID completionId, UUID userId, UUID courseId, Instant revertedAt) {
        for (AwardedBadge badge : awards.findIssuedAutoBadges(userId)) {
            definitions.findById(badge.definitionId())
                .ifPresent(definition -> reevaluate(definition, badge, userId, completionId, revertedAt));
        }
    }

    private void award(BadgeDefinition definition, UUID userId, UUID courseId, Target target, UUID completionId,
        Instant at) {
        Optional<AwardedBadge> existing = awards.lockAwarded(userId, definition.id());
        if (existing.isEmpty()) {
            UUID badgeId = UUID.randomUUID();
            if (awards.insertIfAbsent(badgeId, userId, courseId, definition, target.level(), at)) {
                record(badgeId, LearningBadgeEvent.Type.ISSUED, null, target.level(), target.issueReason(), at);
                LOGGER.info("BADGE_AWARDED definition={} completion={}", definition.code(), completionId);
                return;
            }
            // 다른 서버가 먼저 넣었다. 그 행을 잠그고 이어서 판단한다.
            existing = awards.lockAwarded(userId, definition.id());
        }
        existing.ifPresent(badge -> update(definition, badge, courseId, target, completionId, at));
    }

    private void update(BadgeDefinition definition, AwardedBadge badge, UUID courseId, Target target,
        UUID completionId, Instant at) {
        if (badge.status() == LearningBadgeStatus.REVOKED) {
            if (badge.credentialId() != null || badge.revokedByAdmin()) {
                return;
            }
            if (awards.restore(badge.id(), courseId, target.level(), at)) {
                record(badge.id(), LearningBadgeEvent.Type.RESTORED, null, target.level(), target.restoreReason(), at);
                LOGGER.info("BADGE_RESTORED definition={} completion={}", definition.code(), completionId);
            }
            return;
        }
        Integer current = badge.tierLevel();
        if (badge.status() == LearningBadgeStatus.ISSUED && target.level() != null && current != null
            && target.level() > current && awards.changeLevel(badge.id(), target.level(), at)) {
            record(badge.id(), LearningBadgeEvent.Type.RENEWED, current, target.level(),
                target.riseReason(tierName(definition, current)), at);
            LOGGER.info("BADGE_RENEWED definition={} level={} completion={}", definition.code(), target.level(),
                completionId);
        }
    }

    private void reevaluate(BadgeDefinition definition, AwardedBadge badge, UUID userId, UUID completionId,
        Instant at) {
        if (definition.rule() instanceof BadgeRule.Tiered tiered) {
            long count = awards.countCompletedCourses(userId, prefix(tiered.classificationCode()));
            Optional<BadgeRule.TierLevel> level = tiered.levelFor(count);
            Integer current = badge.tierLevel();
            if (level.isEmpty()) {
                revoke(definition, badge, current, "수료 확정 취소로 강좌 이수 " + count + "건 — 첫 단계 미달", completionId, at);
            } else if (current != null && level.get().level() < current
                && awards.changeLevel(badge.id(), level.get().level(), at)) {
                record(badge.id(), LearningBadgeEvent.Type.RENEWED, current, level.get().level(),
                    "수료 확정 취소로 단계 조정 — " + tierName(definition, current) + "→" + level.get().name(), at);
            }
            return;
        }
        if (!stillSatisfied(definition.rule(), userId)) {
            revoke(definition, badge, null, "수료 확정 취소로 발급 조건 미충족", completionId, at);
        }
    }

    private void revoke(BadgeDefinition definition, AwardedBadge badge, Integer fromLevel, String reason,
        UUID completionId, Instant at) {
        if (awards.revoke(badge.id(), at)) {
            record(badge.id(), LearningBadgeEvent.Type.REVOKED, fromLevel, null, reason, at);
            LOGGER.info("BADGE_REVOKED definition={} completion={}", definition.code(), completionId);
        }
    }

    private void record(UUID badgeId, LearningBadgeEvent.Type type, Integer from, Integer to, String reason,
        Instant at) {
        events.save(LearningBadgeEvent.of(badgeId, type, from, to, reason, null, at));
    }

    /** 이번 확정으로 받을 자격이 되면 대상(단계 배지면 단계 포함), 아니면 null. */
    private Target target(BadgeRule rule, UUID userId, UUID courseId, String courseClassification) {
        return switch (rule) {
            case BadgeRule.CourseCompletion completion -> {
                boolean matched = completion.courseId() != null
                    ? completion.courseId().equals(courseId)
                    : ClassificationPrefix.contains(completion.classificationCode(), courseClassification);
                yield matched ? Target.simple() : null;
            }
            case BadgeRule.Cumulative cumulative ->
                awards.countCompletedCourses(userId, prefix(cumulative.classificationCode())) >= cumulative.count()
                    ? Target.simple() : null;
            case BadgeRule.Tiered tiered -> {
                long count = awards.countCompletedCourses(userId, prefix(tiered.classificationCode()));
                yield tiered.levelFor(count).map(level -> Target.tier(level, count)).orElse(null);
            }
        };
    }

    private boolean stillSatisfied(BadgeRule rule, UUID userId) {
        return switch (rule) {
            case BadgeRule.CourseCompletion completion -> completion.courseId() != null
                ? awards.countCompletedCourse(userId, completion.courseId()) > 0
                : awards.countCompletedCourses(userId, prefix(completion.classificationCode())) > 0;
            case BadgeRule.Cumulative cumulative ->
                awards.countCompletedCourses(userId, prefix(cumulative.classificationCode())) >= cumulative.count();
            case BadgeRule.Tiered tiered ->
                tiered.levelFor(awards.countCompletedCourses(userId, prefix(tiered.classificationCode()))).isPresent();
        };
    }

    private static String prefix(String classificationCode) {
        return classificationCode == null ? null : ClassificationPrefix.of(classificationCode);
    }

    private static String tierName(BadgeDefinition definition, int level) {
        return definition.rule() instanceof BadgeRule.Tiered tiered
            ? tiered.level(level).map(BadgeRule.TierLevel::name).orElse(level + "단계")
            : level + "단계";
    }

    /** 발급 대상. 단계 배지면 도달 단계와 이수 건수를 싣는다. */
    private record Target(Integer level, String levelName, long count) {

        static Target simple() {
            return new Target(null, null, 0);
        }

        static Target tier(BadgeRule.TierLevel level, long count) {
            return new Target(level.level(), level.name(), count);
        }

        String issueReason() {
            return level == null ? "수료 확정으로 발급" : "강좌 이수 " + count + "건 — " + levelName + " 단계 발급";
        }

        String restoreReason() {
            return level == null ? "수료 확정으로 발급 조건 재충족" : "강좌 이수 " + count + "건 — " + levelName + " 단계로 복구";
        }

        String riseReason(String fromName) {
            return "강좌 이수 " + count + "건 달성 — " + fromName + "→" + levelName;
        }
    }
}
