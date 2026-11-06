package com.adn.dabaeum.badge.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 배지 발급 규칙 (설계서 §11.1). 분류 코드는 어느 단계든 받고 하위 분류를 포함한다(계획 3 의 접두사 규칙).
 */
public sealed interface BadgeRule permits BadgeRule.CourseCompletion, BadgeRule.Cumulative, BadgeRule.Tiered {

    String CODE_PATTERN = "^TSIX_[0-9]{6}$";
    int MAX_COUNT = 1000;
    int MIN_TIERS = 2;
    int MAX_TIERS = 10;
    int MAX_TIER_NAME = 20;

    BadgeRuleType type();

    String classificationCode();

    /** 그 강좌, 또는 그 분류(하위 포함)의 어떤 강좌를 수료하면. 둘 중 정확히 하나. */
    record CourseCompletion(UUID courseId, String classificationCode) implements BadgeRule {

        public CourseCompletion {
            if ((courseId == null) == (classificationCode == null)) {
                throw new IllegalArgumentException("exactly one of courseId and classificationCode is required");
            }
            requireCode(classificationCode);
        }

        @Override
        public BadgeRuleType type() {
            return BadgeRuleType.COURSE_COMPLETION;
        }
    }

    /** 수료 누적 count 개(분류를 주면 그 분류·하위 안에서). */
    record Cumulative(int count, String classificationCode) implements BadgeRule {

        public Cumulative {
            if (count < 1 || count > MAX_COUNT) {
                throw new IllegalArgumentException("count must be between 1 and " + MAX_COUNT);
            }
            requireCode(classificationCode);
        }

        @Override
        public BadgeRuleType type() {
            return BadgeRuleType.CUMULATIVE;
        }
    }

    /** 단계 하나. level 은 1부터, minCount 는 이 단계에 드는 최소 이수 건수. 이미지는 단계별로 선택. */
    record TierLevel(int level, String name, int minCount, UUID imageFileId) {

        public TierLevel(int level, String name, int minCount) {
            this(level, name, minCount, null);
        }
    }

    /**
     * 이수 건수로 단계를 정한다(설계서 §11.4). 사람당 배지 1개가 단계를 올린다.
     * level 은 1부터 연속, minCount 는 1 이상 오름차순, 이름은 비어 있지 않고 서로 다르다.
     */
    record Tiered(List<TierLevel> levels, String classificationCode) implements BadgeRule {

        public Tiered {
            if (levels == null || levels.size() < MIN_TIERS || levels.size() > MAX_TIERS) {
                throw new IllegalArgumentException("levels must have " + MIN_TIERS + " to " + MAX_TIERS + " entries");
            }
            levels = List.copyOf(levels);
            Set<String> names = new HashSet<>();
            int previousMin = 0;
            for (int index = 0; index < levels.size(); index++) {
                TierLevel tier = levels.get(index);
                if (tier == null || tier.level() != index + 1) {
                    throw new IllegalArgumentException("levels must be numbered 1.." + levels.size());
                }
                if (tier.name() == null || tier.name().isBlank() || tier.name().length() > MAX_TIER_NAME
                    || !names.add(tier.name().trim())) {
                    throw new IllegalArgumentException("level names must be distinct and 1.." + MAX_TIER_NAME + " chars");
                }
                if (tier.minCount() <= previousMin || tier.minCount() > MAX_COUNT) {
                    throw new IllegalArgumentException("minCount must increase from 1 up to " + MAX_COUNT);
                }
                previousMin = tier.minCount();
            }
            requireCode(classificationCode);
        }

        @Override
        public BadgeRuleType type() {
            return BadgeRuleType.TIERED;
        }

        /** 이수 건수로 도달한 가장 높은 단계. 첫 단계에도 못 미치면 empty. */
        public Optional<TierLevel> levelFor(long completedCount) {
            TierLevel reached = null;
            for (TierLevel tier : levels) {
                if (completedCount >= tier.minCount()) {
                    reached = tier;
                }
            }
            return Optional.ofNullable(reached);
        }

        /** 단계 수·기준 건수·분류가 같은지. 이름·이미지만 다른 변경은 발급 뒤에도 허용한다. */
        public boolean sameThresholds(Tiered other) {
            return other != null && java.util.Objects.equals(classificationCode, other.classificationCode)
                && levels.stream().map(TierLevel::minCount).toList()
                    .equals(other.levels.stream().map(TierLevel::minCount).toList());
        }

        public Optional<TierLevel> level(int level) {
            return level >= 1 && level <= levels.size() ? Optional.of(levels.get(level - 1)) : Optional.empty();
        }
    }

    private static void requireCode(String code) {
        if (code != null && !code.matches(CODE_PATTERN)) {
            throw new IllegalArgumentException("classificationCode must be TSIX_AABBCC");
        }
    }
}
