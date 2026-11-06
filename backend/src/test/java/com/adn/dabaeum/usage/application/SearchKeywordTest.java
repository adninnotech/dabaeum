package com.adn.dabaeum.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** docs/specs/usage-statistics.md §3.3, §4.2 — 검색어 정규화와 저장하지 않는 형태. */
class SearchKeywordTest {

    @Test
    void trimsCollapsesSpacesAndLowercases() {
        assertThat(SearchKeyword.normalize("  Python   기초 \t강좌 ")).isEqualTo("python 기초 강좌");
    }

    @Test
    void cutsAtFiftyCharacters() {
        assertThat(SearchKeyword.normalize("가".repeat(80))).hasSize(50);
    }

    @Test
    void masksPhoneLikeAndEmailLikeQueries() {
        assertThat(SearchKeyword.normalize("010-1234-5678")).isEqualTo(SearchKeyword.MASKED);
        assertThat(SearchKeyword.normalize("학번 2026123")).isEqualTo(SearchKeyword.MASKED);
        assertThat(SearchKeyword.normalize("me@example.com")).isEqualTo(SearchKeyword.MASKED);
    }

    @Test
    void keepsQueriesWithFewDigits() {
        assertThat(SearchKeyword.normalize("2026 엑셀")).isEqualTo("2026 엑셀");
        assertThat(SearchKeyword.normalize("c#")).isEqualTo("c#");
    }
}
