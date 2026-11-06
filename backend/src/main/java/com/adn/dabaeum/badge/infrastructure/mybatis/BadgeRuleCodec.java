package com.adn.dabaeum.badge.infrastructure.mybatis;

import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.badge.domain.BadgeRuleType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 규칙 파라미터 JSON (설계서 §11.1·§11.4): {"courseId"} | {"classificationCode"} | {"count", "classificationCode"?}
 * | {"levels": [{"level", "name", "minCount"}], "classificationCode"?}.
 */
final class BadgeRuleCodec {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private BadgeRuleCodec() {
    }

    static String toJson(BadgeRule rule) {
        Map<String, Object> params = new LinkedHashMap<>();
        switch (rule) {
            case BadgeRule.CourseCompletion completion -> {
                if (completion.courseId() != null) {
                    params.put("courseId", completion.courseId().toString());
                }
                if (completion.classificationCode() != null) {
                    params.put("classificationCode", completion.classificationCode());
                }
            }
            case BadgeRule.Cumulative cumulative -> {
                params.put("count", cumulative.count());
                if (cumulative.classificationCode() != null) {
                    params.put("classificationCode", cumulative.classificationCode());
                }
            }
            case BadgeRule.Tiered tiered -> {
                params.put("levels", tiered.levels().stream()
                    .map(tier -> {
                        Map<String, Object> level = new LinkedHashMap<>();
                        level.put("level", tier.level());
                        level.put("name", tier.name());
                        level.put("minCount", tier.minCount());
                        if (tier.imageFileId() != null) {
                            level.put("imageFileId", tier.imageFileId().toString());
                        }
                        return level;
                    })
                    .toList());
                if (tiered.classificationCode() != null) {
                    params.put("classificationCode", tiered.classificationCode());
                }
            }
        }
        return JSON.writeValueAsString(params);
    }

    static BadgeRule fromJson(String type, String json) {
        JsonNode node = JSON.readTree(json);
        String code = node.hasNonNull("classificationCode") ? node.get("classificationCode").asString() : null;
        return switch (BadgeRuleType.valueOf(type)) {
            case COURSE_COMPLETION -> new BadgeRule.CourseCompletion(
                node.hasNonNull("courseId") ? UUID.fromString(node.get("courseId").asString()) : null, code);
            case CUMULATIVE -> new BadgeRule.Cumulative(node.get("count").asInt(), code);
            case TIERED -> new BadgeRule.Tiered(levels(node.get("levels")), code);
        };
    }

    private static List<BadgeRule.TierLevel> levels(JsonNode array) {
        List<BadgeRule.TierLevel> levels = new ArrayList<>();
        for (JsonNode level : array) {
            levels.add(new BadgeRule.TierLevel(
                level.get("level").asInt(), level.get("name").asString(), level.get("minCount").asInt(),
                level.hasNonNull("imageFileId") ? UUID.fromString(level.get("imageFileId").asString()) : null));
        }
        return levels;
    }
}
