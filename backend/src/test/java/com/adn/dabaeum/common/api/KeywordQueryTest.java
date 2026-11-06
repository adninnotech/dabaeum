package com.adn.dabaeum.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KeywordQueryTest {

    @Test
    void null_과_공백은_조건없음이다() {
        assertThat(KeywordQuery.normalize(null)).isNull();
        assertThat(KeywordQuery.normalize("")).isNull();
        assertThat(KeywordQuery.normalize("   ")).isNull();
    }

    @Test
    void 앞뒤_공백을_없애고_소문자로_만든다() {
        assertThat(KeywordQuery.normalize("  DaTa  ")).isEqualTo("data");
    }

    @Test
    void LIKE_와일드카드를_이스케이프한다() {
        assertThat(KeywordQuery.normalize("100%")).isEqualTo("100\\%");
        assertThat(KeywordQuery.normalize("a_b")).isEqualTo("a\\_b");
        assertThat(KeywordQuery.normalize("back\\slash")).isEqualTo("back\\\\slash");
    }

    @Test
    void 너무_긴_키워드는_거부한다() {
        assertThatThrownBy(() -> KeywordQuery.normalize("가".repeat(101)))
            .isInstanceOf(ApiException.class);
    }
}
