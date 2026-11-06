package com.adn.dabaeum.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.file.infrastructure.mybatis.FileAccessMapper;
import com.adn.dabaeum.support.RemotePostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 단계 배지의 단계별 이미지(rule_params.levels[].imageFileId)도 배지 이미지로 연결된 것으로 본다. */
class FileAccessMapperIntegrationTest extends RemotePostgresIntegrationTestSupport {

    private static final String BADGE_IMAGE = "BADGE_IMAGE";
    private static final Instant NOW = Instant.parse("2026-10-26T00:00:00Z");

    @Autowired FileAccessMapper mapper;

    @Test
    void tierLevelImageOfActiveDefinitionIsPublic() {
        UUID tierImage = UUID.randomUUID();
        insertTieredDefinition(tierImage, true);

        assertThat(mapper.linked(tierImage, BADGE_IMAGE)).isTrue();
        assertThat(mapper.publiclyLinked(tierImage, BADGE_IMAGE, NOW)).isTrue();
    }

    @Test
    void tierLevelImageOfInactiveDefinitionIsLinkedButNotPublic() {
        UUID tierImage = UUID.randomUUID();
        insertTieredDefinition(tierImage, false);

        assertThat(mapper.linked(tierImage, BADGE_IMAGE)).isTrue();
        assertThat(mapper.publiclyLinked(tierImage, BADGE_IMAGE, NOW)).isFalse();
    }

    @Test
    void tierLevelImageIsNotLinkedForOtherPurposeOrUnknownFile() {
        UUID tierImage = UUID.randomUUID();
        insertTieredDefinition(tierImage, true);

        assertThat(mapper.linked(tierImage, "BANNER")).isFalse();
        assertThat(mapper.linked(UUID.randomUUID(), BADGE_IMAGE)).isFalse();
        assertThat(mapper.publiclyLinked(UUID.randomUUID(), BADGE_IMAGE, NOW)).isFalse();
    }

    private void insertTieredDefinition(UUID tierImage, boolean active) {
        String levels = """
            {"levels": [
              {"level": 1, "name": "씨앗", "minCount": 1},
              {"level": 2, "name": "새싹", "minCount": 3, "imageFileId": "%s"}
            ]}""".formatted(tierImage);
        jdbcTemplate.update("""
            INSERT INTO tb_badge_definitions (id, code, name, rule_type, rule_params, active)
            VALUES (?, ?, '단계 배지', 'TIERED', CAST(? AS jsonb), ?)""",
            UUID.randomUUID(), "TIER_IMG_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
            levels, active);
    }
}
