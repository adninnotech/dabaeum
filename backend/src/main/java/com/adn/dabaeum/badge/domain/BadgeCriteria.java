package com.adn.dabaeum.badge.domain;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 규칙을 사람이 읽는 이수기준 문장으로 (설계서 §11.4). 화면이 그대로 보여 준다. */
public final class BadgeCriteria {

    private BadgeCriteria() {
    }

    /**
     * pathOf 는 분류 코드의 위 단계부터 이름 목록(모르는 코드면 빈 목록). 문장에는 코드 대신 가장 아래 이름을 쓴다.
     */
    public static String describe(BadgeRule rule, Function<String, List<String>> pathOf) {
        if (rule == null) {
            return null;
        }
        return switch (rule) {
            case BadgeRule.CourseCompletion completion -> completion.courseId() != null
                ? "지정 강좌 이수"
                : scope(completion.classificationCode(), pathOf) + "강좌 1건 이수";
            case BadgeRule.Cumulative cumulative ->
                scope(cumulative.classificationCode(), pathOf) + "강좌 누적 " + cumulative.count() + "건 이수";
            case BadgeRule.Tiered tiered ->
                scope(tiered.classificationCode(), pathOf) + "강좌 이수 건수 — " + tiers(tiered.levels());
        };
    }

    private static String scope(String classificationCode, Function<String, List<String>> pathOf) {
        if (classificationCode == null) {
            return "";
        }
        List<String> path = pathOf.apply(classificationCode);
        return (path == null || path.isEmpty() ? "지정" : path.getLast()) + " 분야 ";
    }

    private static String tiers(List<BadgeRule.TierLevel> levels) {
        return levels.stream()
            .map(level -> {
                int index = level.level() - 1;
                boolean last = index == levels.size() - 1;
                return last
                    ? level.name() + " " + level.minCount() + "건 이상"
                    : level.name() + " " + level.minCount() + "~" + (levels.get(index + 1).minCount() - 1) + "건";
            })
            .collect(Collectors.joining(", "));
    }
}
