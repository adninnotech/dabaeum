package com.adn.dabaeum.badge.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.adn.dabaeum.badge.domain.BadgeRule;
import com.adn.dabaeum.badge.domain.LearningBadge;
import com.adn.dabaeum.badge.domain.LearningBadgeEvent;
import com.adn.dabaeum.badge.domain.LearningBadgeStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 설계서 §11.4 — 배지 응답의 식별·단계·이수기준·최근 변동. */
class LearningBadgeApiMapperTest {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private final com.adn.dabaeum.classification.application.CourseClassificationService classifications =
        org.mockito.Mockito.mock(com.adn.dabaeum.classification.application.CourseClassificationService.class);
    private final LearningBadgeApiMapper mapper =
        new LearningBadgeApiMapper(Clock.fixed(NOW, ZoneOffset.UTC), "다배움", provider());

    private org.springframework.beans.factory.ObjectProvider<com.adn.dabaeum.classification.application.CourseClassificationService> provider() {
        org.springframework.beans.factory.support.StaticListableBeanFactory factory =
            new org.springframework.beans.factory.support.StaticListableBeanFactory();
        factory.addBean("classifications", classifications);
        return factory.getBeanProvider(com.adn.dabaeum.classification.application.CourseClassificationService.class);
    }

    @Test
    void tieredBadgeShowsLevelNameImageAndCriteria() {
        UUID levelImage = UUID.randomUUID();
        BadgeRule.Tiered rule = new BadgeRule.Tiered(List.of(
            new BadgeRule.TierLevel(1, "씨앗", 1), new BadgeRule.TierLevel(2, "새싹", 6, levelImage),
            new BadgeRule.TierLevel(3, "자람", 11)), null);
        UUID badgeId = UUID.randomUUID();
        LearningBadgeEvent last = new LearningBadgeEvent(UUID.randomUUID(), badgeId, LearningBadgeEvent.Type.RENEWED,
            null, null, "강좌 이수 6건 달성 — 씨앗→새싹", null, NOW);

        LearningBadgeResponse response = mapper.toResponse(badge(badgeId, UUID.randomUUID(),
            new LearningBadge.Details("김학습", 2, null, rule, last)));

        assertThat(response.issuerName()).isEqualTo("다배움");
        assertThat(response.recipientName()).isEqualTo("김학습");
        assertThat(response.tierLevel()).isEqualTo(2);
        assertThat(response.tierName()).isEqualTo("새싹");
        assertThat(response.imageUrl()).isEqualTo("/api/v1/files/" + levelImage + "/content");
        assertThat(response.criteria()).contains("씨앗 1~5건").contains("새싹 6~10건").contains("자람 11건 이상");
        assertThat(response.expiresAt()).isNull();
        assertThat(response.lastEvent().type()).isEqualTo("RENEWED");
        assertThat(response.lastEvent().reason()).contains("씨앗→새싹");
    }

    @Test
    void cumulativeCriteriaAndDefinitionImage() {
        org.mockito.Mockito.when(classifications.pathOf("TSIX_040000")).thenReturn(List.of("문화예술교육"));
        UUID definitionImage = UUID.randomUUID();
        LearningBadgeResponse response = mapper.toResponse(badge(UUID.randomUUID(), definitionImage,
            new LearningBadge.Details("김학습", null, null, new BadgeRule.Cumulative(3, "TSIX_040000"), null)));

        assertThat(response.tierName()).isNull();
        assertThat(response.imageUrl()).isEqualTo("/api/v1/files/" + definitionImage + "/content");
        assertThat(response.criteria()).isEqualTo("문화예술교육 분야 강좌 누적 3건 이수");
        assertThat(response.lastEvent()).isNull();
    }

    private static LearningBadge badge(UUID id, UUID definitionImage, LearningBadge.Details details) {
        return new LearningBadge(id, UUID.randomUUID(), null, null, "TIERED", "평생학습 이수 단계",
            LearningBadgeStatus.ISSUED, null, NOW, null, NOW, NOW, UUID.randomUUID(), "LIFELONG_LEARNING_TIER",
            definitionImage, details);
    }
}
